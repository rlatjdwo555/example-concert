package sts;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Concert_table")
public class Concert {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String name;
    private Integer ticketQty;

    @PostPersist
    public void onPostPersist(){
        ConcertRegistered concertRegistered = new ConcertRegistered();
        BeanUtils.copyProperties(this, concertRegistered);
        concertRegistered.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        TicketUpdated ticketUpdated = new TicketUpdated();
        BeanUtils.copyProperties(this, ticketUpdated);
        ticketUpdated.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public Integer getTicketQty() {
        return ticketQty;
    }

    public void setTicketQty(Integer ticketQty) {
        this.ticketQty = ticketQty;
    }




}
