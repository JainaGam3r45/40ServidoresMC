package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Response of GET /api/vote/v3/pending.
 */
@Data
public class PendingVotesResponse {
    @SerializedName("api_version")
    private int apiVersion;
    private String jugador;
    private ServerInfo servidor;
    @SerializedName("votos_pendientes")
    private List<PendingVote> votosPendientes;
    @SerializedName("reserva_segundos")
    private int reservaSegundos;
    @SerializedName("puede_votar_ya")
    private boolean puedeVotarYa;
    @SerializedName("siguiente_voto")
    private String siguienteVoto;

    public List<PendingVote> safePendingVotes() {
        return votosPendientes == null ? Collections.<PendingVote>emptyList() : votosPendientes;
    }

    public boolean hasPendingVotes() {
        return !safePendingVotes().isEmpty();
    }

    @Data
    public static class ServerInfo {
        private int id;
        private String nombre;
        private String slug;
        private int puesto;
    }
}
