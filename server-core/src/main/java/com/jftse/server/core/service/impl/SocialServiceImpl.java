package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.ServerType;
import com.jftse.entities.database.model.gameserver.GameServer;
import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.model.messenger.EFriendshipState;
import com.jftse.entities.database.model.messenger.Friend;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.gameserver.GameServerRepository;
import com.jftse.server.core.client.FTFriend;
import com.jftse.server.core.constants.FTChannelType;
import com.jftse.server.core.service.FriendService;
import com.jftse.server.core.service.GuildService;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.util.ServerTypeTranslator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {
    private final FriendService friendService;
    private final GuildService guildService;
    private final GameServerRepository gameServerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Friend> getFriendList(Player player, EFriendshipState friendshipState) {
        return friendService.findWithFriendByPlayer(player).stream()
                .filter(x -> x.getEFriendshipState() == friendshipState)
                .sorted(Comparator.comparing(p -> (!p.getFriend().getOnline())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FTFriend> getFTFriendList(Player player, EFriendshipState friendshipState) {
        List<Friend> friendList = getFriendList(player, friendshipState);

        List<FTFriend> result = new ArrayList<>();
        for (Friend entry : friendList) {
            Player friend = entry.getFriend();
            if (!friend.getOnline()) {
                FTFriend ftFriend = new FTFriend(FTChannelType.NONE.getValue(), friend.getId(), friend.getName(), friend.getPlayerType());
                result.add(ftFriend);
                continue;
            }

            ServerType serverType = friend.getAccount() != null ? friend.getAccount().getLoggedInServer() : ServerType.NONE;
            FTChannelType channelType = ServerTypeTranslator.toChannelType(serverType);

            if (channelType == FTChannelType.NONE) {
                FTFriend ftFriend = new FTFriend(FTChannelType.NONE.getValue(), friend.getId(), friend.getName(), friend.getPlayerType());
                result.add(ftFriend);
            } else {
                Optional<GameServer> optGameServer = gameServerRepository.findByChannelType((byte) channelType.getValue());
                if (optGameServer.isPresent()) {
                    GameServer gameServer = optGameServer.get();
                    FTFriend ftFriend = new FTFriend(gameServer.getId(), friend.getId(), friend.getName(), friend.getPlayerType());
                    result.add(ftFriend);
                } else {
                    FTFriend ftFriend = new FTFriend(FTChannelType.NONE.getValue(), friend.getId(), friend.getName(), friend.getPlayerType());
                    result.add(ftFriend);
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Friend> getFriendListByFriend(Player player, EFriendshipState friendshipState) {
        return friendService.findWithPlayerByFriend(player).stream()
                .filter(x -> x.getEFriendshipState() == friendshipState)
                .sorted(Comparator.comparing(p -> (!p.getPlayer().getOnline())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Friend getRelationship(Player player) {
        return friendService.findByPlayer(player).stream()
                .filter(x -> x.getEFriendshipState() == EFriendshipState.Relationship)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Friend getRelationshipWithFriend(Player player) {
        return friendService.findWithFriendByPlayer(player).stream()
                .filter(x -> x.getEFriendshipState() == EFriendshipState.Relationship)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuildMember> getGuildMemberList(Player player) {
        Guild guild = guildService.findWithMembersByPlayerId(player.getId());
        if (guild != null) {
            return guild.getMemberList().stream()
                    .filter(gm -> !gm.getPlayer().getId().equals(player.getId()) && !gm.getWaitingForApproval())
                    .sorted(Comparator.comparing(p -> (!p.getPlayer().getOnline())))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional(readOnly = true)
    public FTFriend toFTFriend(Friend friend) {
        Player friendPlayer = friend.getFriend();
        if (!friendPlayer.getOnline()) {
            return new FTFriend(FTChannelType.NONE.getValue(), friendPlayer.getId(), friendPlayer.getName(), friendPlayer.getPlayerType());
        }

        ServerType serverType = friendPlayer.getAccount() != null ? friendPlayer.getAccount().getLoggedInServer() : ServerType.NONE;
        FTChannelType channelType = ServerTypeTranslator.toChannelType(serverType);

        if (channelType == FTChannelType.NONE) {
            return new FTFriend(FTChannelType.NONE.getValue(), friendPlayer.getId(), friendPlayer.getName(), friendPlayer.getPlayerType());
        } else {
            Optional<GameServer> optGameServer = gameServerRepository.findByChannelType((byte) channelType.getValue());
            if (optGameServer.isPresent()) {
                GameServer gameServer = optGameServer.get();
                return new FTFriend(gameServer.getId(), friendPlayer.getId(), friendPlayer.getName(), friendPlayer.getPlayerType());
            } else {
                return new FTFriend(FTChannelType.NONE.getValue(), friendPlayer.getId(), friendPlayer.getName(), friendPlayer.getPlayerType());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FTFriend toFTFriend(Player player) {
        if (!player.getOnline()) {
            return new FTFriend(FTChannelType.NONE.getValue(), player.getId(), player.getName(), player.getPlayerType());
        }

        ServerType serverType = player.getAccount() != null ? player.getAccount().getLoggedInServer() : ServerType.NONE;
        FTChannelType channelType = ServerTypeTranslator.toChannelType(serverType);

        if (channelType == FTChannelType.NONE) {
            return new FTFriend(FTChannelType.NONE.getValue(), player.getId(), player.getName(), player.getPlayerType());
        } else {
            Optional<GameServer> optGameServer = gameServerRepository.findByChannelType((byte) channelType.getValue());
            if (optGameServer.isPresent()) {
                GameServer gameServer = optGameServer.get();
                return new FTFriend(gameServer.getId(), player.getId(), player.getName(), player.getPlayerType());
            } else {
                return new FTFriend(FTChannelType.NONE.getValue(), player.getId(), player.getName(), player.getPlayerType());
            }
        }
    }
}
