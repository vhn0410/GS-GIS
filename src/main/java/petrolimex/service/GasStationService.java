package petrolimex.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import petrolimex.dto.GasStationRequest;
import petrolimex.model.Address;
import petrolimex.model.Anchor;
import petrolimex.model.DMN;
import petrolimex.model.GasStation;
import petrolimex.model.HasOutput;
import petrolimex.model.HasProduct;
import petrolimex.model.OtherService;
import petrolimex.model.Product;
import petrolimex.model.Pump;

@ApplicationScoped
public class GasStationService {

    @Transactional
    public GasStation create(GasStationRequest req) {
        // Address
        Address address = new Address();
        address.oldAddress = req.address_old;
        address.newAddress = req.address_new;
        address.persist();

        // Anchor
        Anchor anchor = new Anchor();
        anchor.geometry = req.coordinates;
        anchor.address = address;
        anchor.persist();

        // GasStation
        GasStation gs = new GasStation();
        gs.stationName = req.name;
        gs.ownerName = req.owner;
        gs.stationType = req.station_type;
        gs.supplier = req.supplier;
        gs.status = req.status;
        gs.facadeLength = req.facade_length;
        gs.image = req.image;
        gs.note = req.notes;
        gs.address = address;
        gs.nearest_petrolimex_name = req.nearest_petrolimex.name;
        gs.nearest_petrolimex_distance = req.nearest_petrolimex.distance_km;
        gs.persist();

        // DMN
        if(req.dmn != null) {
            for(String dmnName : req.dmn) {    
                DMN dmn = new DMN();
                dmn.dmnName = dmnName;
                dmn.gasStation = gs;
                dmn.persist();
            }
        }

        // Other Services
        if(req.other_services != null) {
            for(String svcName : req.other_services) {
                OtherService svc = new OtherService();
                svc.serviceName = svcName;
                svc.gasStation = gs;
                svc.persist();
            }
        }

        // Products (fuel + oil)
        List<Product> products = new ArrayList<>();
        if(req.estimated_sale != null) {
            if(req.estimated_sale.fuel != null) {
                Product fuel = new Product();
                fuel.productName = "Fuel";
                fuel.persist();
                products.add(fuel);

                // HasOutput
                HasOutput ho = new HasOutput();
                ho.product = fuel;
                ho.gasStation = gs;
                ho.estimateOutput = req.estimated_sale.fuel.doubleValue();
                ho.measurementTime = LocalDate.now();
                ho.persist();
            }
            if(req.estimated_sale.oil != null) {
                Product oil = new Product();
                oil.productName = "Oil";
                oil.persist();
                products.add(oil);

                HasOutput ho = new HasOutput();
                ho.product = oil;
                ho.gasStation = gs;
                ho.estimateOutput = req.estimated_sale.oil.doubleValue();
                ho.measurementTime = LocalDate.now();
                ho.persist();
            }
        }

        // Pumps and HasProduct
        if(req.pumps_quantity != null) {
            for(int i = 0; i < req.pumps_quantity.fuel; i++) {
                Pump pump = new Pump();
                pump.pumpName = "Fuel Pump " + (i + 1);
                pump.gasStation = gs;
                pump.persist();

                // link pump to fuel product
                products.stream()
                        .filter(p -> p.productName.equals("Fuel"))
                        .forEach(p -> {
                            HasProduct hp = new HasProduct();
                            hp.pump = pump;
                            hp.product = p;
                            hp.persist();
                        });
            }
            for(int i = 0; i < req.pumps_quantity.oil; i++) {
                Pump pump = new Pump();
                pump.pumpName = "Oil Pump " + (i + 1);
                pump.gasStation = gs;
                pump.persist();

                products.stream()
                        .filter(p -> p.productName.equals("Oil"))
                        .forEach(p -> {
                            HasProduct hp = new HasProduct();
                            hp.pump = pump;
                            hp.product = p;
                            hp.persist();
                        });
            }
        }

        return gs;
    }
}

