package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.emblem.PlayerEmblemEquipment;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.repository.emblem.PlayerEmblemEquipmentRepository;
import com.jftse.server.core.service.PlayerEmblemEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerEmblemEquipmentServiceImpl implements PlayerEmblemEquipmentService {
    private final PlayerEmblemEquipmentRepository repository;

    @Override
    @Transactional
    public PlayerEmblemEquipment createIfAbsent(Player player) {
        return repository.findByPlayerId(player.getId()).orElseGet(() -> {
            PlayerEmblemEquipment equipment = new PlayerEmblemEquipment();
            equipment.setPlayer(player);
            return repository.save(equipment);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerEmblemEquipment findByPlayerId(Long playerId) {
        return repository.findByPlayerId(playerId).orElse(null);
    }
}
