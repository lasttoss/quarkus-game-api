package io.zw.game.api.configs;

import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.zw.game.api.constants.ApiErrorEnum;
import io.zw.game.api.constants.Principal;
import io.zw.game.api.constants.RedisConstants;
import io.zw.game.api.services.RedisService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
@GlobalInterceptor
public class AuthorizationServerInterceptor implements ServerInterceptor {
    @Inject
    JWTParser parser;

    @Inject
    RedisService redisService;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        Status status;
        try {
            String value = metadata.get(Principal.AUTHORIZATION_METADATA_KEY);
            String tokenStr = value.substring(Principal.BEARER_TYPE.length()).trim();
            JsonWebToken token = parser.parse(tokenStr);
            String key = RedisConstants.REFRESH_TOKEN + token.getSubject();
            if (value == null) {
                status = Status.UNAUTHENTICATED.withDescription(ApiErrorEnum.FAILED_TO_VERIFY_TOKEN.getMessage());
                serverCall.close(status, metadata);
                return new ServerCall.Listener<>() {
                    // noop
                };
            }
            System.out.println(key);
            if (!redisService.checkIfKeyExists(key)) {
                status = Status.UNAUTHENTICATED.withDescription(ApiErrorEnum.FAILED_TO_VERIFY_TOKEN.getMessage());
                serverCall.close(status, metadata);
                return new ServerCall.Listener<>() {
                    // noop
                };
            }
            Context ctx = Context.current().withValue(Principal.CLIENT_ID_CONTEXT_KEY, token.getSubject());
            return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
        } catch (ParseException e) {
            status = Status.UNAUTHENTICATED.withDescription(ApiErrorEnum.FAILED_TO_VERIFY_TOKEN.getMessage());
            serverCall.close(status, metadata);
            return new ServerCall.Listener<>() {
                // noop
            };
        }

    }
}
