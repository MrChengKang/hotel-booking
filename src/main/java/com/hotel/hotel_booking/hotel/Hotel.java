package com.hotel.hotel_booking.hotel;

import com.hotel.hotel_booking.user.Role;
import com.hotel.hotel_booking.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stay_hotels",
    uniqueConstraints = @UniqueConstraint(name = "uk_stay_hotels_slug", columnNames = "slug"),
    indexes = {
        @Index(name = "ix_stay_hotels_host", columnList = "host_id"),
        @Index(name = "ix_stay_hotels_destination", columnList = "country_code,city,status")
    })
@Check(name = "ck_stay_hotels_status", constraints = "status in ('DRAFT','PUBLISHED','ARCHIVED')")
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stay_hotels_host"))
    private User host;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 180) private String slug;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, length = 500) private String address;
    @Column(nullable = false, length = 100) private String city;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "time_zone", nullable = false, length = 64) private String timeZone;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16) private HotelStatus status = HotelStatus.DRAFT;
    @Column(nullable=false,length=32,columnDefinition="varchar(32) default 'NOT_SUBMITTED'") private String reviewStatus="NOT_SUBMITTED";
    @Column(length=1000) private String reviewNote;
    private Instant submittedAt;
    private Instant reviewedAt;
    private Long reviewedById;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<HotelFacility> facilities = new java.util.HashSet<>();

    public java.util.Set<HotelAmenity> getAmenities() {
        return facilities.stream().map(HotelFacility::getAmenity).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public void editAmenities(java.util.Collection<HotelAmenity> values) {
        if (values == null) return; // Older clients preserve existing amenities.
        if (status == HotelStatus.ARCHIVED || reviewStatus.equals("PENDING"))
            throw new IllegalStateException("This hotel cannot be edited now.");
        var checked = java.util.Set.copyOf(values);
        if (!getAmenities().equals(checked)) {
            facilities.removeIf(value -> !checked.contains(value.getAmenity()));
            var existing = getAmenities();
            checked.stream().filter(value -> !existing.contains(value)).forEach(value -> facilities.add(new HotelFacility(this, value)));
            unpublish();
        }
    }

    protected Hotel() {}
    public Hotel(User host, String name, String slug, String description, String address,
            String city, String countryCode, String currencyCode, String timeZone) {
        if (host == null || host.getRole() != Role.HOST) throw new IllegalArgumentException("Hotel owner must have the HOST role.");
        this.host = host;
        this.name = ModelRules.text(name, "Hotel name", 160);
        this.slug = ModelRules.text(slug, "Hotel slug", 180);
        if (!this.slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Hotel slug must use lowercase letters, numbers and hyphens.");
        this.description = description == null ? "" : description.strip();
        if (this.description.length() > 10000) throw new IllegalArgumentException("Description must be at most 10000 characters.");
        this.address = ModelRules.text(address, "Address", 500);
        this.city = ModelRules.text(city, "City", 100);
        this.countryCode = ModelRules.text(countryCode, "Country code", 2).toUpperCase(Locale.ROOT);
        if (!java.util.Set.of(Locale.getISOCountries()).contains(this.countryCode)) throw new IllegalArgumentException("Use an ISO two-letter country code.");
        this.currencyCode = Currency.getInstance(ModelRules.text(currencyCode, "Currency code", 3).toUpperCase(Locale.ROOT)).getCurrencyCode();
        this.timeZone = ZoneId.of(ModelRules.text(timeZone, "Time zone", 64)).getId();
    }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void edit(String name,String description,String address,String city) {
        if(status==HotelStatus.ARCHIVED) throw new IllegalStateException("Archived hotels cannot be edited.");
        if(reviewStatus.equals("PENDING"))throw new IllegalStateException("Withdraw the review request before editing this hotel.");
        var checked=new Hotel(host,name,slug,description,address,city,countryCode,currencyCode,timeZone);
        boolean changed=!this.name.equals(checked.name)||!this.description.equals(checked.description)||!this.address.equals(checked.address)||!this.city.equals(checked.city);
        this.name=checked.name; this.description=checked.description; this.address=checked.address; this.city=checked.city;
        if(changed)unpublish();
    }
    public void unpublish() {
        if(status==HotelStatus.ARCHIVED) throw new IllegalStateException("Archived hotels cannot be reopened.");
        status=HotelStatus.DRAFT;
        reviewStatus="NOT_SUBMITTED";reviewNote=null;submittedAt=null;reviewedAt=null;reviewedById=null;
    }
    public void publish() {
        if (status == HotelStatus.ARCHIVED) throw new IllegalStateException("Archived hotels cannot be published.");
        status = HotelStatus.PUBLISHED;
        reviewStatus="APPROVED";
    }
    public void submitForReview(){
        if(status!=HotelStatus.DRAFT||reviewStatus.equals("PENDING"))throw new IllegalStateException("Only a draft or rejected hotel can be submitted for review.");
        reviewStatus="PENDING";submittedAt=Instant.now();reviewedAt=null;reviewedById=null;reviewNote=null;
    }
    public void decideReview(boolean approved,String note,Long adminId){
        if(status!=HotelStatus.DRAFT||!reviewStatus.equals("PENDING"))throw new IllegalStateException("This hotel is no longer awaiting review.");
        reviewStatus=approved?"APPROVED":"REJECTED";status=approved?HotelStatus.PUBLISHED:HotelStatus.DRAFT;
        reviewNote=note;reviewedAt=Instant.now();reviewedById=adminId;
    }
    public void archive() { status = HotelStatus.ARCHIVED;if(reviewStatus.equals("PENDING"))reviewStatus="NOT_SUBMITTED"; }
    public String getReviewStatus(){return reviewStatus;}public String getReviewNote(){return reviewNote;}
    public Instant getSubmittedAt(){return submittedAt;}public Instant getReviewedAt(){return reviewedAt;}
    public Long getReviewedById(){return reviewedById;}
    public Long getId() { return id; }
    public User getHost() { return host; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getCountryCode() { return countryCode; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTimeZone() { return timeZone; }
    public HotelStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
