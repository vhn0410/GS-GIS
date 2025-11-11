package petrolimex.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;

@Entity
@Table(name = "HAS_PRODUCT")
public class HasProduct extends PanacheEntityBase {

    @Id
    @ManyToOne
    @JoinColumn(name = "PUMP_ID")
    public Pump pump;

    @Id
    @ManyToOne
    @JoinColumn(name = "PRODUCT_ID")
    public Product product;
}
