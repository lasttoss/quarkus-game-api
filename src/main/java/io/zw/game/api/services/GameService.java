package io.zw.game.api.services;

import com.google.gson.Gson;
import io.quarkus.grpc.GrpcService;
import io.zw.GameGrpc;
import io.zw.GameRequest;
import io.zw.GameResponse;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.constants.GameOpCode;
import io.zw.game.api.constants.Principal;
import io.zw.game.api.dto.response.ErrorDTO;
import io.zw.game.api.dto.response.ResultDTO;
import io.smallrye.mutiny.Uni;
import io.vertx.grpc.common.GrpcStatus;
import jakarta.inject.Inject;

@GrpcService
public class GameService implements GameGrpc {

    @Inject
    UserPlantService userPlantService;

    Gson gson = new Gson();

    @Override
    public Uni<GameResponse> sendData(GameRequest request) {
        String userId = Principal.CLIENT_ID_CONTEXT_KEY.get();
        String payload = request.getPayload();
        switch (request.getOpcode()) {
            case GameOpCode
                    .PLANT_PROGRESS_INFO: {
                ResultDTO result = userPlantService.getInfo(userId);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
            }
            case GameOpCode.PLANT_PROGRESS_SOW: {
                ResultDTO result = userPlantService.sowSeed(userId, payload);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
            }
            case GameOpCode.PLANT_PROGRESS_SPRAY_WATER: {
                ResultDTO result = userPlantService.sprayWater(userId, payload);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
            }
            case GameOpCode.PLANT_PROGRESS_PICKING: {
                ResultDTO result = userPlantService.pickingFruit(userId);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
            }
            case GameOpCode.PLANT_PROGRESS_PROTECT_RESOURCE: {
                ResultDTO result = userPlantService.protectResource(userId, payload);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
            }
            default:
                ResultDTO result = new ResultDTO();
                ErrorDTO error = new ErrorDTO(ApiErrorEnum.OPCODE_NOT_FOUND);
                result.setStatus(GrpcStatus.ABORTED.code);
                result.setError(error);
                return Uni.createFrom().item("Send Data").map(msg -> GameResponse.newBuilder().setOpcode(GameOpCode.PLANT_PROGRESS_INFO).setData(gson.toJson(result)).build());
        }

    }
}
