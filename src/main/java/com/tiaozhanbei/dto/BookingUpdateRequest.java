package com.tiaozhanbei.dto;

public class BookingUpdateRequest {
    private String status;
    private String appointmentTime;
    private String contactMethod;
    private String bookingNote;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }
    public String getContactMethod() { return contactMethod; }
    public void setContactMethod(String contactMethod) { this.contactMethod = contactMethod; }
    public String getBookingNote() { return bookingNote; }
    public void setBookingNote(String bookingNote) { this.bookingNote = bookingNote; }
}
