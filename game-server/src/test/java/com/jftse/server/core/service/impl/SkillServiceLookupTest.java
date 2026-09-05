package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.battle.Skill;
import com.jftse.entities.database.repository.battle.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceLookupTest {
    @Test
    void lookupUsesDatabaseIdNotRepositoryOrderAndPreservesIndexGaps() {
        Skill first = new Skill();
        first.setId(1L);
        Skill third = new Skill();
        third.setId(3L);
        SkillRepository repository = mock(SkillRepository.class);
        when(repository.findAll()).thenReturn(List.of(third, first));
        SkillServiceImpl service = new SkillServiceImpl(repository);
        service.init();

        assertSame(first, service.findSkillById(1L));
        assertSame(third, service.findSkillById(3L));
        assertSame(first, service.findSkillByIndex(0));
        assertSame(third, service.findSkillByIndex(2));
        assertNull(service.findSkillById(2L));
        assertNull(service.findSkillByIndex(1));
        assertNull(service.findSkillById(0L));
        assertNull(service.findSkillById(4_294_967_297L));
        assertNull(service.findSkillByIndex(-1));
        assertNull(service.findSkillByIndex(Integer.MAX_VALUE));
    }
}
