package clients.symphony.api;

import clients.SymBotClient;
import clients.symphony.api.constants.AgentConstants;
import clients.symphony.api.constants.CommonConstants;
import clients.symphony.api.constants.PodConstants;
import exceptions.*;
import model.*;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.*;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MessagesClient extends APIClient{
    private SymBotClient botClient;

    public MessagesClient(SymBotClient client) {
        botClient = client;
    }

    public InboundMessage sendMessage(String streamId, OutboundMessage message) throws SymClientException {
        //TODO: Add file support

                ClientConfig clientConfig = new ClientConfig();
                clientConfig.register(MultiPartFeature.class);
                clientConfig.register(JacksonFeature.class);

                Client httpClient =  ClientBuilder.newClient(clientConfig);
                WebTarget target = httpClient.target(CommonConstants.HTTPSPREFIX + botClient.getConfig().getAgentHost() + ":" + botClient.getConfig().getAgentPort())
                        .path(AgentConstants.CREATEMESSAGE.replace("{sid}", streamId));

                Invocation.Builder invocationBuilder = target.request().accept(new String[]{"application/json"});

                invocationBuilder = invocationBuilder.header("sessionToken",botClient.getSymBotAuth().getSessionToken());
                invocationBuilder = invocationBuilder.header("keyManagerToken", botClient.getSymBotAuth().getKmToken());

                FormDataMultiPart multiPart = new FormDataMultiPart();

                FormDataContentDisposition contentDisp = FormDataContentDisposition.name("message").build();
                multiPart.bodyPart(new FormDataBodyPart(contentDisp, message.getMessage()));
                Entity entity = Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE);
                Response response = invocationBuilder.post(entity);


                int statusCode = response.getStatusInfo().getStatusCode();
                if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
                    return null;
                }

                if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                    handleError(response,botClient);
                    return null;
                }
                else {
                    return response.readEntity(InboundMessage.class);
                }

    }

    public List<InboundMessage> getMessagesFromStream(String streamId, int since, int skip, int limit) throws SymClientException {
        List<InboundMessage> result = null;
        Client client = ClientBuilder.newClient();
        WebTarget builder
                = client.target(CommonConstants.HTTPSPREFIX + botClient.getConfig().getAgentHost() + ":" + botClient.getConfig().getAgentHost())
                .path(AgentConstants.GETMESSAGES.replace("{sid}", streamId))
                .queryParam("since", since);


        if(skip>0){
            builder.queryParam("skip", skip);
        }
        if(limit>0){
            builder.queryParam("limit", limit);
        }
        Response response = builder.request(MediaType.APPLICATION_JSON)
                .header("sessionToken",botClient.getSymBotAuth().getSessionToken())
                .header("keyManagerToken", botClient.getSymBotAuth().getKmToken())
                .get();

        if(response.getStatus() == 204){
            result = new ArrayList<>();
        } else if (response.getStatus() == 200) {
            result = response.readEntity(InboundMessageList.class);
        }
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            handleError(response, botClient);
            return null;
        }
        return result;
    }

    public byte[] getAttachment(String streamId, String attachmentId, String messageId) throws SymClientException {
        Client client = ClientBuilder.newClient();
        Response response
                = client.target(CommonConstants.HTTPSPREFIX + botClient.getConfig().getAgentHost() + ":" + botClient.getConfig().getAgentHost())
                .path(AgentConstants.GETATTACHMENT.replace("{sid}", streamId))
                .queryParam("fileId", attachmentId)
                .queryParam("messageId", messageId)
                .request(MediaType.APPLICATION_JSON)
                .header("sessionToken",botClient.getSymBotAuth().getSessionToken())
                .header("keyManagerToken", botClient.getSymBotAuth().getKmToken())
                .get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            handleError(response, botClient);
            return null;
        } else {
            return Base64.getDecoder().decode(response.readEntity(String.class));
        }
    }

    public List<FileAttachment> getMessageAttachments(InboundMessage message) throws SymClientException {
        List<FileAttachment> result = new ArrayList<>();
        for (Attachment attachment : message.getAttachments()) {
            FileAttachment fileAttachment = new FileAttachment();
            fileAttachment.setFileName(attachment.getName());
            fileAttachment.setSize(attachment.getSize());
            fileAttachment.setFileContent(getAttachment(message.getStream().getStreamId(), attachment.getId(), message.getMessageId()));
            result.add(fileAttachment);
        }
        return result;
    }

    public MessageStatus getMessageStatus(String messageId) throws SymClientException {
        Client client = ClientBuilder.newClient();
        Response response
                = client.target(CommonConstants.HTTPSPREFIX + botClient.getConfig().getPodHost() + ":" + botClient.getConfig().getPodPort())
                .path(PodConstants.GETMESSAGESTATUS.replace("{mid}", messageId))
                .request(MediaType.APPLICATION_JSON)
                .header("sessionToken",botClient.getSymBotAuth().getSessionToken())
                .get();
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            handleError(response, botClient);
            return null;
        }
        return response.readEntity(MessageStatus.class);

    }

}
