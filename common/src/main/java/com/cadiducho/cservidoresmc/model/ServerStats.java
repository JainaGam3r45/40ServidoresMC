package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class ServerStats {
    @SerializedName("nombre") private String serverName;
    @SerializedName("puesto") private int position;
    @SerializedName("votos") private Integer totalVotes;
    @SerializedName("votoshoy") private int dayVotes;
    @SerializedName("votosmensuales") private Integer monthVotes;
    @SerializedName("votoshoypremiados") private int rewardedDayVotes;
    @SerializedName("votossemanales") private int weekVotes;
    @SerializedName("votossemanalespremiados") private int rewardedWeekVotes;
    @SerializedName("ultimos20votos") private List<ServerVote> lastVotes;
}
