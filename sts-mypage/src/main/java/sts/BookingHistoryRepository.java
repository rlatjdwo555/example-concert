package sts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface BookingHistoryRepository extends PagingAndSortingRepository<BookingHistory, Long>{
	List<BookingHistory> findByUserId(Long userId);
	Optional<BookingHistory> findByBookingId(Long bookingId);
}