package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of POST /api/vote/v3/ack.
 * entregado=true confirms reward delivery; false releases the reservation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AckRequest {
    private List<Long> votos;
    private boolean entregado;
    private String nick;
    @SerializedName("user_ip")
    private String userIp;
}
