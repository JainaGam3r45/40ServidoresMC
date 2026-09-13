package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Response of POST /api/vote/v3/ack.
 */
@Data
public class AckResponse {
    @SerializedName("api_version")
    private int apiVersion;
    private List<Long> confirmados;
    @SerializedName("ya_confirmados")
    private List<Long> yaConfirmados;
    private List<Long> liberados;
    private List<Long> desconocidos;
    private boolean entregado;
}
