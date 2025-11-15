package petrolimex.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import petrolimex.dto.GasStationRequest;
import petrolimex.dto.GasStationResponse;
import petrolimex.model.Address;
import petrolimex.model.Anchor;
import petrolimex.model.DMN;
import petrolimex.model.GasStation;
import petrolimex.model.HasOutput;
import petrolimex.model.HasProduct;
import petrolimex.model.OtherService;
import petrolimex.model.Product;
import petrolimex.model.Pump;
import java.util.Map;

@ApplicationScoped
public class GasStationService {

    @Inject
    EntityManager em;

    @Transactional
    public GasStationResponse getById(Long id) {
        GasStation gs = GasStation.findById(id);
        if (gs == null) return null;

        GasStationResponse res = new GasStationResponse();

        // basic
        res.name = gs.stationName;
        res.owner = gs.ownerName;
        res.supplier = gs.supplier;
        res.station_type = gs.stationType;
        res.status = gs.status;
        res.facade_length = gs.facadeLength;
        res.image = gs.image;
        res.notes = gs.note;

        // address
        if (gs.address != null) {
            res.address_old = gs.address.oldAddress;
            res.address_new = gs.address.newAddress;
        }

        // coordinates (anchor)
        Anchor anchor = Anchor.find("address", gs.address).firstResult();
        if (anchor != null) res.coordinates = anchor.geometry;

        // nearest petrolimex
        res.nearest_petrolimex = new GasStationResponse.Nearest();
        res.nearest_petrolimex.name = gs.nearest_petrolimex_name;
        res.nearest_petrolimex.distance_km = gs.nearest_petrolimex_distance;

        // DMN (list of string)
        res.dmn = DMN.find(
            "select d.dmnName from DMN d where d.gasStation = ?1",
            gs
        ).project(String.class).list();

        // Other services
        res.other_services = OtherService.find(
            "select s.serviceName from OtherService s where s.gasStation = ?1",
            gs
        ).project(String.class).list();

        // Estimated sale (fuel + oil)
        List<HasOutput> outputs = HasOutput.find("gasStation", gs).list();

        GasStationResponse.EstimatedSale est = new GasStationResponse.EstimatedSale();
        est.fuel = outputs.stream()
                .filter(h -> h.product.productName.equals("Fuel"))
                .map(h -> h.estimateOutput)
                .findFirst().orElse(null);

        est.oil = outputs.stream()
                .filter(h -> h.product.productName.equals("Oil"))
                .map(h -> h.estimateOutput)
                .findFirst().orElse(null);

        if (est.fuel != null && est.oil != null)
            est.total = est.fuel + est.oil;
        else if (est.fuel != null)
            est.total = est.fuel;
        else if (est.oil != null)
            est.total = est.oil;

        res.estimated_sale = est;

        // Pumps (count)
        List<Pump> pumps = Pump.find("gasStation", gs).list();

        GasStationResponse.PumpsQuantity pq = new GasStationResponse.PumpsQuantity();
        pq.fuel = (int) pumps.stream()
                            .filter(p -> p.pumpName.startsWith("Fuel"))
                            .count();
        pq.oil = (int) pumps.stream()
                            .filter(p -> p.pumpName.startsWith("Oil"))
                            .count();

        res.pumps_quantity = pq;

        return res;
    }



    @Transactional
    public GasStation create(GasStationRequest req) {
        
        if(req.name == null || req.name.isEmpty()) {
            throw new IllegalArgumentException("Gas station name is required");
        }

        if(req.coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        
        // Address
        Address address = new Address();
        address.oldAddress = req.address_old != null ? req.address_old : null;
        address.newAddress = req.address_new != null ? req.address_new : null;
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
            if(req.pumps_quantity.fuel != null) {
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
            }
            if(req.pumps_quantity.oil != null) {
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
        }

        return gs;
    }

    @Transactional
    public GasStation update(Long id, GasStationRequest req) {
        GasStation gs = GasStation.findById(id);
        if (gs == null) {
            throw new IllegalArgumentException("GasStation with id " + id + " not found");
        }

        if(req.name == null || req.name.isEmpty()) {
            throw new IllegalArgumentException("Gas station name is required");
        }

        if (req.coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        // Update basic fields
        gs.stationName = req.name;
        gs.ownerName = req.owner;
        gs.stationType = req.station_type;
        gs.supplier = req.supplier;
        gs.status = req.status;
        gs.facadeLength = req.facade_length;
        gs.image = req.image;
        gs.note = req.notes;
        gs.nearest_petrolimex_name = req.nearest_petrolimex != null ? req.nearest_petrolimex.name : null;
        gs.nearest_petrolimex_distance = req.nearest_petrolimex != null ? req.nearest_petrolimex.distance_km : null;

        // Update or Create Address
        if (gs.address != null) {
            // Update existing Address
            gs.address.oldAddress = req.address_old;
            gs.address.newAddress = req.address_new;
            
            // Update Anchor geometry if coordinates changed
            Anchor anchor = Anchor.find("address.id", gs.address.id).firstResult();
            if (anchor != null && req.coordinates != null) {
                anchor.geometry = req.coordinates;
            }
        } 
        // else {
        //     // Create new Address and Anchor if not exists
        //     Address address = new Address();
        //     address.oldAddress = req.address_old;
        //     address.newAddress = req.address_new;
        //     address.persist();

        //     Anchor anchor = new Anchor();
        //     anchor.geometry = req.coordinates;
        //     anchor.address = address;
        //     anchor.persist();

        //     gs.address = address;
        // }

        // Update DMN - remove old ones and add new ones
        DMN.delete("gasStation.id", gs.id);
        if(req.dmn != null) {
            for(String dmnName : req.dmn) {
                DMN dmn = new DMN();
                dmn.dmnName = dmnName;
                dmn.gasStation = gs;
                dmn.persist();
            }
        }

        // Update Other Services - remove old ones and add new ones
        OtherService.delete("gasStation.id", gs.id);
        if(req.other_services != null) {
            for(String svcName : req.other_services) {
                OtherService svc = new OtherService();
                svc.serviceName = svcName;
                svc.gasStation = gs;
                svc.persist();
            }
        }

        // Update Products and HasOutput
        // Xóa HasOutput cũ và lưu productIds để xóa Product không dùng nữa
        List<HasOutput> oldOutputs = HasOutput.list("gasStation.id", gs.id);
        List<Long> oldProductIds = new ArrayList<>();
        for (HasOutput output : oldOutputs) {
            if (output.product != null) {
                oldProductIds.add(output.product.id);
            }
        }
        HasOutput.delete("gasStation.id", gs.id);

        // Tạo Products mới
        List<Product> products = new ArrayList<>();
        if(req.estimated_sale != null) {
            if(req.estimated_sale.fuel != null) {
                Product fuel = new Product();
                fuel.productName = "Fuel";
                fuel.persist();
                products.add(fuel);

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

        // Xóa Products cũ nếu không còn được dùng
        for (Long productId : oldProductIds) {
            long count = HasOutput.count("product.id", productId);
            if (count == 0) {
                Product.deleteById(productId);
            }
        }

        // Update Pumps and HasProduct
        // Xóa HasProduct và Pumps cũ
        List<Pump> oldPumps = Pump.list("gasStation.id", gs.id);
        for (Pump pump : oldPumps) {
            HasProduct.delete("pump.id", pump.id);
        }
        Pump.delete("gasStation.id", gs.id);

        // Tạo Pumps mới
        if(req.pumps_quantity != null) {
            if(req.pumps_quantity.fuel != null) {
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
            }
            if(req.pumps_quantity.oil != null) {
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
        }

        return gs;
    }

    @Transactional
    public void delete(Long id) {
        GasStation gs = GasStation.findById(id);
        if (gs == null) {
            throw new IllegalArgumentException("GasStation with id " + id + " not found");
        }

        // Lưu addressId trước khi xóa GasStation
        Long addressId = gs.address != null ? gs.address.id : null;

        // Kiểm tra Address có được dùng bởi GasStation khác không (trước khi xóa)
        long addressUsageCount = 0;
        if (addressId != null) {
            addressUsageCount = GasStation.count("address.id = ?1 and id != ?2", addressId, id);
        }

        // Step 1: Delete Anchor (depends on Address)
        if (addressId != null) {
            em.createQuery("DELETE FROM Anchor a WHERE a.address.id = :addressId")
                .setParameter("addressId", addressId)
                .executeUpdate();
        }

        // Step 2: Delete DMN
        em.createQuery("DELETE FROM DMN d WHERE d.gasStation.id = :gsId")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 3: Delete OtherServices
        em.createQuery("DELETE FROM OtherService o WHERE o.gasStation.id = :gsId")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 4: Delete HasProduct (junction table)
        em.createQuery("DELETE FROM HasProduct hp WHERE hp.pump.id IN " +
                      "(SELECT p.id FROM Pump p WHERE p.gasStation.id = :gsId)")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 5: Lấy productIds từ HasOutput trước khi xóa
        List<Long> productIds = em.createQuery(
            "SELECT ho.product.id FROM HasOutput ho WHERE ho.gasStation.id = :gsId", Long.class)
            .setParameter("gsId", id)
            .getResultList();

        // Xóa HasOutput
        em.createQuery("DELETE FROM HasOutput ho WHERE ho.gasStation.id = :gsId")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 6: Delete Products (only if not used by other gas stations)
        for (Long productId : productIds) {
            long count = em.createQuery(
                "SELECT COUNT(ho) FROM HasOutput ho WHERE ho.product.id = :productId", Long.class)
                .setParameter("productId", productId)
                .getSingleResult();
            
            if (count == 0) {
                em.createQuery("DELETE FROM Product p WHERE p.id = :productId")
                    .setParameter("productId", productId)
                    .executeUpdate();
            }
        }

        // Step 7: Delete Pumps
        em.createQuery("DELETE FROM Pump p WHERE p.gasStation.id = :gsId")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 8: Flush và clear để đảm bảo không còn reference
        em.flush();
        em.clear();

        // Step 9: Delete GasStation
        em.createQuery("DELETE FROM GasStation gs WHERE gs.id = :gsId")
            .setParameter("gsId", id)
            .executeUpdate();

        // Step 10: Delete Address (chỉ xóa nếu không có GasStation nào khác dùng)
        if (addressId != null && addressUsageCount == 0) {
            em.createQuery("DELETE FROM Address a WHERE a.id = :addressId")
                .setParameter("addressId", addressId)
                .executeUpdate();
        }

        em.flush();
    }

}

