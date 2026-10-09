package io.zw.game.api.services;

import io.vertx.grpc.common.GrpcStatus;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.dto.response.ResultDTO;
import io.zw.GameRequest;
import io.zw.GameResponse;
import io.zw.game.api.constants.GameOpCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The gRPC front door: it reads an opcode off the request, calls the one service method that matches it
 * and serialises the answer. There is nothing else in it, which is why these tests are about the two
 * things that could go wrong in a switch - an opcode that reaches the wrong method, and an opcode that
 * reaches none of them - rather than about the plant rules, which the service tests already own.
 *
 * The client id comes off a context the transport fills in, so in a test it is simply absent. Every
 * assertion here is on what was asked of the service rather than on who asked.
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    /** No opcode in GameOpCode carries this number, which is the point of it. */
    static final int UNKNOWN_OPCODE = 9999;

    @Mock
    UserPlantService userPlantService;

    @InjectMocks
    GameService gameService;

    static GameRequest request(int opcode, String payload) {
        return GameRequest.newBuilder().setOpcode(opcode).setPayload(payload).build();
    }

    static GameResponse send(GameService service, int opcode, String payload) {
        return service.sendData(request(opcode, payload)).await().indefinitely();
    }

    static ResultDTO ok() {
        ResultDTO result = new ResultDTO();
        result.setStatus(GrpcStatus.OK.code);
        return result;
    }

    @Test
    void pingAnswersPongWithoutTouchingTheGame() {
        GameResponse response = send(gameService, GameOpCode.PING, "");

        assertEquals(GameOpCode.PING, response.getOpcode());
        assertEquals("PONG", response.getData());
        verifyNoInteractions(userPlantService);
    }

    @Test
    void eachPlantOpcodeReachesItsOwnMethod() {
        when(userPlantService.getInfo(any())).thenReturn(ok());
        when(userPlantService.sowSeed(any(), any())).thenReturn(ok());
        when(userPlantService.sprayWater(any(), any())).thenReturn(ok());
        when(userPlantService.pickingFruit(any())).thenReturn(ok());
        when(userPlantService.protectResource(any(), any())).thenReturn(ok());

        send(gameService, GameOpCode.PLANT_PROGRESS_INFO, "");
        send(gameService, GameOpCode.PLANT_PROGRESS_SOW, "{\"itemId\":\"seed-1\"}");
        send(gameService, GameOpCode.PLANT_PROGRESS_SPRAY_WATER, "{\"quantity\":1}");
        send(gameService, GameOpCode.PLANT_PROGRESS_PICKING, "");
        send(gameService, GameOpCode.PLANT_PROGRESS_PROTECT_RESOURCE, "{\"type\":1}");

        verify(userPlantService).getInfo(any());
        verify(userPlantService).sowSeed(any(), any());
        verify(userPlantService).sprayWater(any(), any());
        verify(userPlantService).pickingFruit(any());
        verify(userPlantService).protectResource(any(), any());
    }

    @Test
    void thePayloadIsHandedToTheServiceUnchanged() {
        when(userPlantService.sowSeed(any(), any())).thenReturn(ok());

        send(gameService, GameOpCode.PLANT_PROGRESS_SOW, "{\"itemId\":\"seed-7\"}");

        verify(userPlantService).sowSeed(any(), org.mockito.ArgumentMatchers.eq("{\"itemId\":\"seed-7\"}"));
    }

    @Test
    void thePlantAnswerIsTheResultAsJson() {
        when(userPlantService.getInfo(any())).thenReturn(ok());

        GameResponse response = send(gameService, GameOpCode.PLANT_PROGRESS_INFO, "");

        assertTrue(response.getData().contains("\"status\":0"), "the result did not travel as json: " + response.getData());
    }

    @Test
    void anOpcodeNobodyKnowsIsRefusedWithoutCallingTheGame() {
        GameResponse response = send(gameService, UNKNOWN_OPCODE, "");

        assertTrue(response.getData().contains("\"code\":" + ApiErrorEnum.OPCODE_NOT_FOUND.getCode()),
                "an unknown opcode should come back as OPCODE_NOT_FOUND: " + response.getData());
        verify(userPlantService, never()).getInfo(any());
        verify(userPlantService, never()).sowSeed(any(), any());
    }

    /**
     * Recorded rather than changed: every plant opcode is answered with PLANT_PROGRESS_INFO, whatever
     * was asked. PING echoes itself, so the response opcode is at least somewhere meant to say what the
     * answer is to; for the five plant operations it says "info" instead of "the thing you asked for".
     * Nothing in this repository depends on the field - the transport pairs request with response - so
     * changing it is a change to what clients see, and that is their decision rather than mine. The test
     * says what happens today so that a decision shows up as a failure.
     */
    @Test
    void everyPlantOpcodeIsAnsweredWithTheInfoOpcode() {
        when(userPlantService.sowSeed(any(), any())).thenReturn(ok());
        when(userPlantService.pickingFruit(any())).thenReturn(ok());

        assertEquals(GameOpCode.PLANT_PROGRESS_INFO, send(gameService, GameOpCode.PLANT_PROGRESS_SOW, "").getOpcode());
        assertEquals(GameOpCode.PLANT_PROGRESS_INFO, send(gameService, GameOpCode.PLANT_PROGRESS_PICKING, "").getOpcode());
    }

    /**
     * The same shape as the guard that was the wrong way round and the config check that asked the wrong
     * question: the answer is not the one the name of the thing says. Here the name is accurate and the
     * behaviour is defensible - a switch with no default has to answer something - so it is pinned.
     */
    @Test
    void anOpcodeNobodyKnowsIsAlsoAnsweredWithTheInfoOpcode() {
        assertEquals(GameOpCode.PLANT_PROGRESS_INFO, send(gameService, UNKNOWN_OPCODE, "").getOpcode());
    }
}
