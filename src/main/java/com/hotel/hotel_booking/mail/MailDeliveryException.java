package com.hotel.hotel_booking.mail;

/** Contains status only: never retain provider bodies, reset links or request headers. */
public final class MailDeliveryException extends RuntimeException {
    private final int httpStatus;
    public MailDeliveryException(int httpStatus) {
        super(httpStatus == 0 ? "Brevo email delivery could not be confirmed. Check the network and mail configuration."
                : "Brevo email delivery could not be confirmed (HTTP " + httpStatus + "). Check sender verification, API permissions, quota and account status.");
        this.httpStatus = httpStatus;
    }
    public int httpStatus() { return httpStatus; }
}
