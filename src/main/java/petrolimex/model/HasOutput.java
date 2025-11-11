package petrolimex.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import java.time.LocalDate;

@Entity
@Table(name = "HAS_OUTPUT")
public class HasOutput extends PanacheEntityBase {

    @Id
    @ManyToOne
    @JoinColumn(name = "PRODUCT_ID")
    public Product product;

    @Id
    @ManyToOne
    @JoinColumn(name = "GAS_STATION_ID")
    public GasStation gasStation;

    @Column(name = "ESTIMATE_OUPUT")
    public Double estimateOutput;

    @Column(name = "MESUREMENT_TIME")
    public LocalDate measurementTime;
}

