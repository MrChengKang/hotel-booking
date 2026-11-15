package com.hotel.hotel_booking.hotel;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;

@Service
@Transactional(readOnly = true)
public class HotelSearchService {
    private final NamedParameterJdbcTemplate jdbc;
    private final HotelManagementService hotels;

    public HotelSearchService(NamedParameterJdbcTemplate jdbc, HotelManagementService hotels) {
        this.jdbc = jdbc;
        this.hotels = hotels;
    }

    public PageResponse<HotelView> search(String city, LocalDate checkIn, LocalDate checkOut,
            int rooms, int guests, int minBeds, BigDecimal minPrice, BigDecimal maxPrice,
            String currency, String sort, List<HotelAmenity> amenities, int page, int size) {
        HotelManagementService.page(page, size);
        String destination = city == null || city.isBlank() ? null : city.strip();
        if (destination != null && destination.length() > 100) throw new IllegalArgumentException("City is too long.");
        boolean dated = checkIn != null || checkOut != null;
        long nights = dated ? HotelManagementService.validateRange(checkIn, checkOut) : 0;
        if (rooms < 1 || rooms > 20 || guests < 1 || guests > 1000 || minBeds < 0 || minBeds > 100)
            throw new IllegalArgumentException("Choose 1–20 rooms, 1–1000 guests and 0–100 beds.");
        validatePrice(minPrice); validatePrice(maxPrice);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price.");
        String code = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
        if (code != null) Currency.getInstance(code);
        String order = switch (sort) {
            case "newest" -> "hotel_id desc";
            case "price_asc" -> "nightly asc, hotel_id desc";
            case "price_desc" -> "nightly desc, hotel_id desc";
            default -> throw new IllegalArgumentException("Unsupported hotel sort.");
        };
        if (code == null && (minPrice != null || maxPrice != null || !sort.equals("newest")))
            throw new IllegalArgumentException("Choose a currency for price filters or price sorting.");
        var selected = amenities == null ? Set.<HotelAmenity>of() : new HashSet<>(amenities);
        if (selected.size() > HotelAmenity.values().length || selected.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Unsupported hotel amenities.");

        var params = new MapSqlParameterSource().addValue("quantity", rooms)
            .addValue("capacity", (guests + rooms - 1) / rooms).addValue("beds", minBeds);
        StringBuilder where = new StringBuilder(" where h.status = 'PUBLISHED' and r.active = true and r.max_guests >= :capacity and r.beds >= :beds and r.total_rooms >= :quantity");
        if (destination != null) { where.append(" and lower(h.city) = lower(:city)"); params.addValue("city", destination); }
        if (code != null) { where.append(" and h.currency_code = :currency"); params.addValue("currency", code); }
        if (!selected.isEmpty()) {
            where.append(" and (select count(*) from stay_hotel_amenities a where a.hotel_id = h.id and a.amenity in (:amenities)) = :amenityCount");
            params.addValue("amenities", selected.stream().map(Enum::name).toList()).addValue("amenityCount", selected.size());
        }
        String offers;
        if (dated) {
            // Compute today's date per hotel zone in Java; MySQL timezone tables are not required.
            var zones = jdbc.queryForList("select distinct time_zone from stay_hotels where status = 'PUBLISHED'", new MapSqlParameterSource(), String.class)
                .stream().filter(zone -> !checkIn.isBefore(LocalDate.now(ZoneId.of(zone)))).toList();
            if (zones.isEmpty()) return new PageResponse<>(List.of(), page, size, 0, 0);
            params.addValue("zones", zones).addValue("checkIn", checkIn).addValue("checkOut", checkOut).addValue("nights", nights);
            where.append(" and h.time_zone in (:zones) and i.stay_date >= :checkIn and i.stay_date < :checkOut and i.stop_sell = false and i.total_rooms - i.booked_rooms - i.blocked_rooms >= :quantity");
            offers = "select h.id as hotel_id, r.id as room_id, round(avg(i.nightly_price), 2) as nightly from stay_hotels h join stay_room_types r on r.hotel_id = h.id join stay_room_inventory i on i.room_type_id = r.id"
                + where + " group by h.id, r.id having count(*) = :nights";
        } else {
            offers = "select h.id as hotel_id, r.id as room_id, r.base_price as nightly from stay_hotels h join stay_room_types r on r.hotel_id = h.id" + where;
        }
        StringBuilder price = new StringBuilder(" where 1 = 1");
        if (minPrice != null) { price.append(" and nightly >= :minPrice"); params.addValue("minPrice", minPrice); }
        if (maxPrice != null) { price.append(" and nightly <= :maxPrice"); params.addValue("maxPrice", maxPrice); }
        // A single room type must match capacity, ALL nights and price. Rank only matching offers.
        String matches = "select * from (" + offers + ") offers" + price;
        long count = Objects.requireNonNull(jdbc.queryForObject("select count(distinct hotel_id) from (" + matches + ") matches", params, Long.class));
        params.addValue("limit", size).addValue("offset", (long) page * size);
        String ranked = "select hotel_id, room_id, nightly, row_number() over (partition by hotel_id order by nightly asc, room_id asc) as position_in_hotel from (" + matches + ") matches";
        var results = jdbc.query("select hotel_id, room_id, nightly from (" + ranked + ") ranked where position_in_hotel = 1 order by " + order + " limit :limit offset :offset",
            params, (row, index) -> hotels.publicHotel(row.getLong("hotel_id")).withSearchPrice(row.getBigDecimal("nightly"), row.getLong("room_id")));
        return new PageResponse<>(results, page, size, count, (int) ((count + size - 1) / size));
    }

    private static void validatePrice(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("9999999999.99")) > 0))
            throw new IllegalArgumentException("Price must be nonnegative with at most 2 decimal places.");
    }
}
