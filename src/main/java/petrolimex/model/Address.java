package petrolimex.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "ADDRESS")
public class Address extends PanacheEntityBase {

    @Id
    @Column(name = "ADDRESS_ID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer id;

    @Column(name = "OLD_ADDRESS")
    public String oldAddress;

    @Column(name = "NEW_ADDRESS")
    public String newAddress;

    @Column(name = "COUNTRY")
    public String country;

    // Quan hệ 1:1 với GasStation
    @OneToOne(mappedBy = "address")
    @JsonIgnore
    public GasStation gasStation;
}
