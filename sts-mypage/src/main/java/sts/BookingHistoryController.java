package sts;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

 @RestController
 public class BookingHistoryController {
	 @Autowired
	 BookingHistoryService bookingHistoryService;
	 
	 @GetMapping(value = "/bookingHistories/{userId}")
	 public String selectBookingHistory(@PathVariable Long userId) {
		 List<BookingHistory> bookingHistory = bookingHistoryService.selectBookingHistory(userId);
		 
		 return bookingHistory.toString();
	 }
 }
