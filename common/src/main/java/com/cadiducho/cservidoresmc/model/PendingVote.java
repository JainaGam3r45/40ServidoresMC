package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Vote returned by GET /api/vote/v3/pending. The id goes back in the ack body.
 */
@Data
public class PendingVote {
    private long id;
    private String fecha;
    private String dia;
    @SerializedName("origen")
    private String origen;
}
