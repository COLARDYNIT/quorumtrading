package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.ChatMessageRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateChatModelRequest;
import com.quorumtrading.adapter.rest.dto.response.ChatSessionResponse;
import com.quorumtrading.adapter.rest.dto.response.ChatSessionSummaryResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.chat.*;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.UUID;

/**
 * REST resource for AI chat interactions.
 * Provides session management and SSE-based message streaming.
 */
@Path("/api/v1/chat")
@ApplicationScoped
@Authenticated
@Tag(name = "Chat", description = "AI assistant chat interactions")
@RegisterForReflection(targets = {
    ChatSessionResponse.class,
    ChatSessionResponse.MessageDto.class,
    ChatSessionResponse.UsageDto.class,
    ChatSessionSummaryResponse.class,
    ChatMessageRequest.class,
    UpdateChatModelRequest.class,
    ChatStreamEvent.TextResponse.class,
    ChatStreamEvent.ToolStart.class,
    ChatStreamEvent.ToolResult.class,
    ChatStreamEvent.Done.class,
    ChatStreamEvent.Error.class,
    FollowUpSuggestion.class,
})
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    @Inject
    CreateChatSessionUseCase createChatSessionUseCase;

    @Inject
    GetChatSessionUseCase getChatSessionUseCase;

    @Inject
    SendChatMessageUseCase sendChatMessageUseCase;

    @Inject
    ListChatSessionsUseCase listChatSessionsUseCase;

    @Inject
    ChatSessionPort chatSessionPort;

    @Inject
    CurrentUserProvider currentUser;

    @POST
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a new chat session", description = "Creates a session with a system prompt and LLM-generated greeting")
    public Response createSession() {
        UserId userId = currentUser.getCurrentUserId();
        CreateChatSessionUseCase.Result result = createChatSessionUseCase.execute(userId);

        ChatSessionResponse response = new ChatSessionResponse(
            result.sessionId(),
            result.greeting(),
            result.model(),
            List.of(new ChatSessionResponse.MessageDto("assistant", result.greeting())),
            new ChatSessionResponse.UsageDto(0, 0, 0, "0"),
            result.suggestions(),
            null
        );

        return Response.ok(response).build();
    }

    @POST
    @Path("/sessions/{sessionId}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking
    @Operation(summary = "Send a message", description = "Sends a user message and returns SSE stream of events")
    public Multi<ChatStreamEvent> sendMessage(
            @PathParam("sessionId") UUID sessionId,
            @Valid ChatMessageRequest request) {
        UserId userId = currentUser.getCurrentUserId();
        LOG.infof("Chat message: sessionId=%s, content=%s", sessionId, truncate(request.content(), 50));
        return sendChatMessageUseCase.execute(sessionId, userId, request.content());
    }

    @GET
    @Path("/sessions/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get session", description = "Retrieves an existing chat session for reconnection")
    public Response getSession(@PathParam("sessionId") UUID sessionId) {
        UserId userId = currentUser.getCurrentUserId();
        ChatSession session = getChatSessionUseCase.execute(sessionId, userId);

        List<ChatSessionResponse.MessageDto> messageDtos = session.getMessages().stream()
            .filter(m -> "user".equals(m.role()) || "assistant".equals(m.role()))
            .map(m -> new ChatSessionResponse.MessageDto(m.role(), m.content()))
            .toList();

        ChatSessionResponse response = new ChatSessionResponse(
            session.getSessionId(),
            null,
            session.getModel(),
            messageDtos,
            ChatSessionResponse.UsageDto.from(session.getCumulativeUsage()),
            List.of(),  // No suggestions for reconnected sessions
            session.getTitle()
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List sessions", description = "Lists all chat sessions for the current user, ordered by most recent")
    public Response listSessions() {
        UserId userId = currentUser.getCurrentUserId();
        List<ChatSessionSummary> summaries = listChatSessionsUseCase.execute(userId);

        List<ChatSessionSummaryResponse> response = summaries.stream()
            .map(s -> new ChatSessionSummaryResponse(
                s.sessionId(), s.title(), s.model(), s.createdAt(), s.lastActivityAt()))
            .toList();

        return Response.ok(response).build();
    }

    @DELETE
    @Path("/sessions/{sessionId}")
    @Operation(summary = "Delete session", description = "Deletes a chat session")
    public Response deleteSession(@PathParam("sessionId") UUID sessionId) {
        UserId userId = currentUser.getCurrentUserId();
        chatSessionPort.delete(sessionId, userId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/sessions/{sessionId}/model")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update session model", description = "Changes the LLM model for this session")
    public Response updateSessionModel(
            @PathParam("sessionId") UUID sessionId,
            @Valid UpdateChatModelRequest request) {
        UserId userId = currentUser.getCurrentUserId();
        try {
            ChatSession session = getChatSessionUseCase.execute(sessionId, userId);
            session.updateModel(request.model());
            chatSessionPort.save(session);
            return Response.ok(java.util.Map.of("model", request.model())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(com.quorumtrading.adapter.rest.exception.ProblemDetails.notFound(
                    "Chat session not found or expired",
                    "/api/v1/chat/sessions/" + sessionId + "/model"))
                .build();
        } catch (Exception e) {
            LOG.errorf("Failed to update chat session model: sessionId=%s, error=%s",
                sessionId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(com.quorumtrading.adapter.rest.exception.ProblemDetails.internalError(
                    "Failed to update session model",
                    "/api/v1/chat/sessions/" + sessionId + "/model"))
                .build();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
