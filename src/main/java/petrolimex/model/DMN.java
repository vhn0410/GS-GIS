package petrolimex.model;

// import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;

@Entity
@Table(name = "DMN")
public class DMN extends PanacheEntityBase {

    @Id
    @Column(name = "DMN_ID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Long id;

    @Column(name = "DMN_NAME")
    public String dmnName;

    @ManyToOne
    @JoinColumn(name = "GAS_STATION_ID")
    public GasStation gasStation;
}
