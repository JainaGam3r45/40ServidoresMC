package com.cadiducho.cservidoresmc.scheduler;

import com.cadiducho.cservidoresmc.api.CSCommandSender;

public interface PlayerTask {

    void run(CSCommandSender sender);

    default void unavailable(PlayerReference player) {
    }
}
