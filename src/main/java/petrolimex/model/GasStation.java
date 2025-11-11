package petrolimex.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "GAS_STATION")
public class GasStation extends PanacheEntityBase {

    @Id
    @Column(name = "GAS_STATION_ID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer id;

    @Column(name = "STATION_NAME")
    public String stationName;

    @Column(name = "OWNER_NAME")
    public String ownerName;

    @Column(name = "STATION_TYPE")
    public String stationType;

    @Column(name = "SUPPLIER")
    public String supplier;

    @Column(name = "STATUS")
    public int status;

    @Column(name = "FACADE_LENGTH")
    public Double facadeLength;

    @Column(name = "IMAGE")
    public String image;
    
    @Column(name = "NOTE")
    public String note;

    // Quan hệ 1:1 với Address
    @OneToOne
    @JoinColumn(name = "ADDRESS_ID", referencedColumnName = "ADDRESS_ID")
    public Address address;
}
