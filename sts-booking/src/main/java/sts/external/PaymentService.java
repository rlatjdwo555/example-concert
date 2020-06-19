package sts.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import sts.Payment;

@FeignClient(name="payment", url="${feign.payment.url}", fallback = PaymentServiceFallback.class)
public interface PaymentService {
    @PostMapping(path="/payments")
    public void requestPayment(Payment payment);
}
