package sts;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookingHistoryService {
	@Autowired
	BookingHistoryRepository bookingHistoryRepository;
	
	public List<BookingHistory> selectBookingHistory(Long userId) {
		return bookingHistoryRepository.findByUserId(userId);
	}
}
