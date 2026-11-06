package cn.blockforge.generated.arcanesingularity.structure;

public enum StructureKind {
    SINGULARITY("singularity"),
    STARGATE("stargate"),
    TIMEBEACON("time_beacon"),
    WORLD_CORE("world_core");

    private final String id;

    StructureKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
