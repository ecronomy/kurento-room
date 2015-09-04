/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.kurento.room;

import java.util.Set;

import javax.annotation.PreDestroy;

import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.KurentoClientSessionInfo;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.RoomEventHandler;
import org.kurento.room.api.UserNotificationService;
import org.kurento.room.api.pojo.ParticipantRequest;
import org.kurento.room.api.pojo.UserParticipant;
import org.kurento.room.exception.RoomException;
import org.kurento.room.exception.RoomException.Code;
import org.kurento.room.internal.DefaultKurentoClientSessionInfo;
import org.kurento.room.internal.DefaultRoomEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Kurento room manager represents an SDK for any developer that wants to
 * implement the Room server-side application. They can build their application
 * on top of the manager’s Java API and implement their desired business logic
 * without having to consider room or media-specific details.
 * 
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
public class RoomManager {
	private final Logger log = LoggerFactory.getLogger(RoomManager.class);

	private RoomEventHandler roomEventHandler;
	private SyncRoomManager internalManager;

	/**
	 * Provides an instance of the room manager by setting an user notification
	 * service that will be used by the default event handler to send responses
	 * and notifications back to the clients.
	 * 
	 * @param notificationService encapsulates the communication layer, used to
	 *        instantiate {@link DefaultRoomEventHandler}
	 * @param kcProvider enables the manager to obtain Kurento Client instances
	 */
	public RoomManager(UserNotificationService notificationService,
			KurentoClientProvider kcProvider) {
		super();
		this.roomEventHandler =
				new DefaultRoomEventHandler(notificationService);
		this.internalManager =
				new SyncRoomManager(roomEventHandler, kcProvider);
	}

	/**
	 * Provides an instance of the room manager by setting an event handler.
	 * 
	 * @param roomEventHandler the room event handler implementation
	 * @param kcProvider enables the manager to obtain Kurento Client instances
	 */
	public RoomManager(RoomEventHandler roomEventHandler,
			KurentoClientProvider kcProvider) {
		super();
		this.roomEventHandler = roomEventHandler;
		this.internalManager =
				new SyncRoomManager(roomEventHandler, kcProvider);
	}

	// ----------------- CLIENT-ORIGINATED REQUESTS ------------
	
	/**
	 * Calls
	 * {@link SyncRoomManager#joinRoom(String, String, boolean, KurentoClientSessionInfo, String)}
	 * with a {@link DefaultKurentoClientSessionInfo} bean as implementation of
	 * the {@link KurentoClientSessionInfo}.
	 * 
	 * @param request instance of {@link ParticipantRequest} POJO containing the
	 *        participant’s id and a request id (optional identifier of the
	 *        request at the communications level, included when responding back
	 *        to the client)
	 * 
	 * @see SyncRoomManager#joinRoom(String, String, boolean,
	 *      KurentoClientSessionInfo, String)
	 */
	public void joinRoom(String userName, String roomName,
			boolean webParticipant, ParticipantRequest request) {
		Set<UserParticipant> existingParticipants = null;
		try {
			KurentoClientSessionInfo kcSessionInfo =
					new DefaultKurentoClientSessionInfo(
							request.getParticipantId(), roomName);
			existingParticipants =
					internalManager.joinRoom(userName, roomName,
							webParticipant, kcSessionInfo,
							request.getParticipantId());
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error joining/creating room {}",
					userName, roomName, e);
			roomEventHandler.onParticipantJoined(request, roomName, userName,
					null, e);
		}
		if (existingParticipants != null)
			roomEventHandler.onParticipantJoined(request, roomName, userName,
					existingParticipants, null);
	}

	/**
	 * @param request instance of {@link ParticipantRequest} POJO
	 * 
	 * @see SyncRoomManager#leaveRoom(String)
	 */
	public void leaveRoom(ParticipantRequest request) {
		String pid = request.getParticipantId();
		Set<UserParticipant> remainingParticipants = null;
		String roomName = null;
		String userName = null;
		try {
			roomName = internalManager.getRoomName(pid);
			userName = internalManager.getParticipantName(pid);
			remainingParticipants = internalManager.leaveRoom(pid);
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error leaving room {}", userName,
					roomName, e);
			roomEventHandler.onParticipantLeft(request, null, null, e);
		}
		if (remainingParticipants != null)
			roomEventHandler.onParticipantLeft(request, userName,
					remainingParticipants, null);
	}

	/**
	 * @param request instance of {@link ParticipantRequest} POJO
	 * 
	 * @see SyncRoomManager#publishMedia(String, boolean, String, MediaElement,
	 *      MediaType, boolean, MediaElement...)
	 */
	public void publishMedia(ParticipantRequest request, boolean isOffer,
			String sdp, MediaElement loopbackAlternativeSrc,
			MediaType loopbackConnectionType, boolean doLoopback,
			MediaElement... mediaElements) {
		String pid = request.getParticipantId();
		String userName = null;
		Set<UserParticipant> participants = null;
		String sdpAnswer = null;
		try {
			userName = internalManager.getParticipantName(pid);
			participants =
					internalManager.getParticipants(internalManager
							.getRoomName(pid));
			sdpAnswer =
					internalManager.publishMedia(request.getParticipantId(),
							isOffer, sdp, loopbackAlternativeSrc,
							loopbackConnectionType, doLoopback, mediaElements);
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error publishing media", userName, e);
			roomEventHandler.onPublishMedia(request, null, null, null, e);
		}
		if (sdpAnswer != null)
			roomEventHandler.onPublishMedia(request, userName, sdpAnswer,
					participants, null);
	}

	/**
	 * @param request instance of {@link ParticipantRequest} POJO
	 * 
	 * @see SyncRoomManager#publishMedia(String, String, boolean,
	 *      MediaElement...)
	 */
	public void publishMedia(ParticipantRequest request, String sdpOffer,
			boolean doLoopback, MediaElement... mediaElements) {
		this.publishMedia(request, true, sdpOffer, null, null, doLoopback,
				mediaElements);
	}

	/**
	 * @param request instance of {@link ParticipantRequest} POJO
	 * 
	 * @see SyncRoomManager#unpublishMedia(String)
	 */
	public void unpublishMedia(ParticipantRequest request) {
		String pid = request.getParticipantId();
		String userName = null;
		Set<UserParticipant> participants = null;
		boolean unpublished = false;
		try {
			userName = internalManager.getParticipantName(pid);
			internalManager.unpublishMedia(pid);
			unpublished = true;
			participants =
					internalManager.getParticipants(internalManager
							.getRoomName(pid));
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error unpublishing media", userName, e);
			roomEventHandler.onUnpublishMedia(request, null, null, e);
		}
		if (unpublished)
			roomEventHandler.onUnpublishMedia(request, userName, participants,
					null);
	}

	/**
	 * @param request instance of {@link ParticipantRequest} POJO
	 * 
	 * @see SyncRoomManager#subscribe(String, String, String)
	 */
	public void subscribe(String remoteName, String sdpOffer,
			ParticipantRequest request) {
		String pid = request.getParticipantId();
		String userName = null;
		String sdpAnswer = null;
		try {
			userName = internalManager.getParticipantName(pid);
			sdpAnswer = internalManager.subscribe(remoteName, sdpOffer, pid);
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error subscribing to {}", userName,
					remoteName, e);
			roomEventHandler.onSubscribe(request, null, e);
		}
		if (sdpAnswer != null)
			roomEventHandler.onSubscribe(request, sdpAnswer, null);
	}

	/**
	 * @see SyncRoomManager#unsubscribe(String, String)
	 * 
	 * @param request instance of {@link ParticipantRequest} POJO
	 */
	public void unsubscribe(String remoteName, ParticipantRequest request) {
		String pid = request.getParticipantId();
		String userName = null;
		boolean unsubscribed = false;
		try {
			userName = internalManager.getParticipantName(pid);
			internalManager.unsubscribe(remoteName, pid);
			unsubscribed = true;
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: unsubscribing from {}", userName,
					remoteName, e);
			roomEventHandler.onUnsubscribe(request, e);
		}
		if (unsubscribed)
			roomEventHandler.onUnsubscribe(request, null);
	}

	/**
	 * @see SyncRoomManager#onIceCandidate(String, String, int, String, String)
	 */
	public void onIceCandidate(String endpointName, String candidate,
			int sdpMLineIndex, String sdpMid, ParticipantRequest request) {
		try {
			internalManager.onIceCandidate(endpointName, candidate,
					sdpMLineIndex, sdpMid, request.getParticipantId());
			roomEventHandler.onRecvIceCandidate(request, null);
		} catch (RoomException e) {
			log.warn("Error receiving ICE candidate", e);
			roomEventHandler.onRecvIceCandidate(request, e);
		}
	}

	/**
	 * Used by clients to send written messages to all other participants in the
	 * room.<br/>
	 * <strong>Side effects:</strong> The room event handler should acknowledge
	 * the client’s request by sending an empty message. Should also send
	 * notifications to the all participants in the room with the message and
	 * its sender.
	 * 
	 * @param message message contents
	 * @param userName name or identifier of the user in the room
	 * @param roomName room’s name
	 * @param request instance of {@link ParticipantRequest} POJO
	 */
	public void sendMessage(String message, String userName, String roomName,
			ParticipantRequest request) {
		log.debug("Request [SEND_MESSAGE] message={} ({})", message, request);
		try {
			if (!internalManager.getParticipantName(request.getParticipantId())
					.equals(userName))
				throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
						"Provided username '" + userName
								+ "' differs from the participant's name");
			if (!internalManager.getRoomName(request.getParticipantId())
					.equals(roomName))
				throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE,
						"Provided room name '" + roomName
								+ "' differs from the participant's room");
			roomEventHandler.onSendMessage(request, message, userName,
					roomName, internalManager.getParticipants(roomName), null);
		} catch (RoomException e) {
			log.warn("PARTICIPANT {}: Error sending message", userName, e);
			roomEventHandler.onSendMessage(request, null, null, null, null, e);
		}
	}

	// ----------------- APPLICATION-ORIGINATED REQUESTS ------------
	/**
	 * @see SyncRoomManager#close()
	 */
	@PreDestroy
	public void close() {
		if (!internalManager.isClosed())
			internalManager.close();
	}

	/**
	 * @see SyncRoomManager#getRooms()
	 */
	public Set<String> getRooms() {
		return internalManager.getRooms();
	}

	/**
	 * @see SyncRoomManager#getParticipants(String)
	 */
	public Set<UserParticipant> getParticipants(String roomName)
			throws RoomException {
		return internalManager.getParticipants(roomName);
	}

	/**
	 * @see SyncRoomManager#getPublishers(String)
	 */
	public Set<UserParticipant> getPublishers(String roomName)
			throws RoomException {
		return internalManager.getPublishers(roomName);
	}

	/**
	 * @see SyncRoomManager#getSubscribers(String)
	 */
	public Set<UserParticipant> getSubscribers(String roomName)
			throws RoomException {
		return internalManager.getSubscribers(roomName);
	}

	/**
	 * @see SyncRoomManager#getPeerPublishers(String)
	 */
	public Set<UserParticipant> getPeerPublishers(String participantId)
			throws RoomException {
		return internalManager.getPeerPublishers(participantId);
	}

	/**
	 * @see SyncRoomManager#getPeerSubscribers(String)
	 */
	public Set<UserParticipant> getPeerSubscribers(String participantId)
			throws RoomException {
		return internalManager.getPeerSubscribers(participantId);
	}

	/**
	 * @see SyncRoomManager#createRoom(KurentoClientSessionInfo)
	 */
	public void createRoom(KurentoClientSessionInfo kcSessionInfo)
			throws RoomException {
		internalManager.createRoom(kcSessionInfo);
	}

	/**
	 * @see SyncRoomManager#getPipeline(String)
	 */
	public MediaPipeline getPipeline(String participantId) throws RoomException {
		return internalManager.getPipeline(participantId);
	}

	/**
	 * Application-originated request to remove a participant from the room. <br/>
	 * <strong>Side effects:</strong> The room event handler should notify the
	 * user that she has been evicted. Should also send notifications to all
	 * other participants about the one that's just been evicted.
	 * 
	 * @see SyncRoomManager#leaveRoom(String)
	 */
	public void evictParticipant(String participantId) throws RoomException {
		UserParticipant participant =
				internalManager.getParticipantInfo(participantId);
		Set<UserParticipant> remainingParticipants =
				internalManager.leaveRoom(participantId);
		roomEventHandler.onParticipantLeft(participant.getUserName(),
				remainingParticipants);
		roomEventHandler.onParticipantEvicted(participant);
	}

	/**
	 * @see SyncRoomManager#closeRoom(String)
	 */
	public void closeRoom(String roomName) throws RoomException {
		Set<UserParticipant> participants = internalManager.closeRoom(roomName);
		roomEventHandler.onRoomClosed(roomName, participants);
	}

	/**
	 * @see SyncRoomManager#generatePublishOffer(String)
	 */
	public String generatePublishOffer(String participantId)
			throws RoomException {
		return internalManager.generatePublishOffer(participantId);
	}

	/**
	 * @see SyncRoomManager#addMediaElement(String, MediaElement)
	 */
	public void addMediaElement(String participantId, MediaElement element)
			throws RoomException {
		internalManager.addMediaElement(participantId, element);
	}

	/**
	 * @see SyncRoomManager#addMediaElement(String, MediaElement, MediaType)
	 */
	public void addMediaElement(String participantId, MediaElement element,
			MediaType type) throws RoomException {
		internalManager.addMediaElement(participantId, element, type);
	}

	/**
	 * @see SyncRoomManager#removeMediaElement(String, MediaElement)
	 */
	public void removeMediaElement(String participantId, MediaElement element)
			throws RoomException {
		internalManager.removeMediaElement(participantId, element);
	}

	/**
	 * @see SyncRoomManager#mutePublishedMedia(MutedMediaType, String)
	 */
	public void mutePublishedMedia(MutedMediaType muteType, String participantId)
			throws RoomException {
		internalManager.mutePublishedMedia(muteType, participantId);
	}

	/**
	 * @see SyncRoomManager#unmutePublishedMedia(String)
	 */
	public void unmutePublishedMedia(String participantId) throws RoomException {
		internalManager.unmutePublishedMedia(participantId);
	}

	/**
	 * @see SyncRoomManager#muteSubscribedMedia(String, MutedMediaType, String)
	 */
	public void muteSubscribedMedia(String remoteName, MutedMediaType muteType,
			String participantId) throws RoomException {
		internalManager
				.muteSubscribedMedia(remoteName, muteType, participantId);
	}

	/**
	 * @see SyncRoomManager#unmuteSubscribedMedia(String, String)
	 */
	public void unmuteSubscribedMedia(String remoteName, String participantId)
			throws RoomException {
		internalManager.unmuteSubscribedMedia(remoteName, participantId);
	}
}
