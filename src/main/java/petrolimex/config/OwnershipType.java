package petrolimex.config;

public enum OwnershipType {

    SO_HUU(0, "Sở hữu"),
    TRUC_THUOC(1, "Trực thuộc"),
    DAI_LY(2, "Đại lý"),
    TNNQ(3, "Thương nhân nhận quyền"),
    HT_KHAC(4, "Khác");

    private final int code;
    private final String label;

    OwnershipType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    // Convert integer → enum  
    public static OwnershipType fromCode(int code) {
        for (OwnershipType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
