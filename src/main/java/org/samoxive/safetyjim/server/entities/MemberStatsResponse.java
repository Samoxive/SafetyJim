package org.samoxive.safetyjim.server.entities;

import java.util.List;

public class MemberStatsResponse {
    public List<Stat> onlineStats;
    public List<Stat> totalStats;

    public MemberStatsResponse(List<Stat> onlineStats, List<Stat> totalStats) {
        this.onlineStats = onlineStats;
        this.totalStats = totalStats;
    }
}
