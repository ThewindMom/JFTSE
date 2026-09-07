package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.battle.Skill;
import com.jftse.entities.database.repository.battle.SkillRepository;
import com.jftse.server.core.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {
    private final SkillRepository skillRepository;

    private Map<Long, Skill> skills;

    @PostConstruct
    public void init() {
        skills = skillRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(Skill::getId, Function.identity()));
    }

    @Override
    public Skill findSkillById(Long id) {
        return skills.get(id);
    }

    @Override
    public Skill findSkillByIndex(int index) {
        return index < 0 ? null : findSkillById(index + 1L);
    }
}
