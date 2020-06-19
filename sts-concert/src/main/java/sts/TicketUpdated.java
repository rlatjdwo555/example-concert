package sts;

public class TicketUpdated extends AbstractEvent {

    private Long id;
    private String name;
    private Integer ticketQty;

    public TicketUpdated(){
        super();
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
