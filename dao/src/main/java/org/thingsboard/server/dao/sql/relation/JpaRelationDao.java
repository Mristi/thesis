/**
 * Copyright © 2016-2017 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.sql.relation;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.util.SqlDao;
import org.thingsboard.server.dao.model.sql.RelationCompositeKey;
import org.thingsboard.server.dao.model.sql.RelationEntity;
import org.thingsboard.server.dao.relation.RelationDao;
import org.thingsboard.server.dao.sql.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.server.dao.sql.JpaAbstractSearchTimeDao;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.jpa.domain.Specifications.where;

/**
 * Created by Valerii Sosliuk on 5/29/2017.
 */
@Slf4j
@Component
@SqlDao
public class JpaRelationDao extends JpaAbstractDaoListeningExecutorService implements RelationDao {

    @Autowired
    private RelationRepository relationRepository;

    @Override
    public ListenableFuture<List<EntityRelation>> findAllByFrom(EntityId from, RelationTypeGroup typeGroup) {
        return service.submit(() -> DaoUtil.convertDataList(
                relationRepository.findAllByFromIdAndFromTypeAndRelationTypeGroup(
                        from.getId(),
                        from.getEntityType().name(),
                        typeGroup.name())));
    }

    @Override
    public ListenableFuture<List<EntityRelation>> findAllByFromAndType(EntityId from, String relationType, RelationTypeGroup typeGroup) {
        return service.submit(() -> DaoUtil.convertDataList(
                relationRepository.findAllByFromIdAndFromTypeAndRelationTypeAndRelationTypeGroup(
                        from.getId(),
                        from.getEntityType().name(),
                        relationType,
                        typeGroup.name())));
    }

    @Override
    public ListenableFuture<List<EntityRelation>> findAllByTo(EntityId to, RelationTypeGroup typeGroup) {
        return service.submit(() -> DaoUtil.convertDataList(
                relationRepository.findAllByToIdAndToTypeAndRelationTypeGroup(
                        to.getId(),
                        to.getEntityType().name(),
                        typeGroup.name())));
    }

    @Override
    public ListenableFuture<List<EntityRelation>> findAllByToAndType(EntityId to, String relationType, RelationTypeGroup typeGroup) {
        return service.submit(() -> DaoUtil.convertDataList(
                relationRepository.findAllByToIdAndToTypeAndRelationTypeAndRelationTypeGroup(
                        to.getId(),
                        to.getEntityType().name(),
                        relationType,
                        typeGroup.name())));
    }

    @Override
    public ListenableFuture<Boolean> checkRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) {
        RelationCompositeKey key =
                new RelationCompositeKey(from.getId(),
                        from.getEntityType().name(),
                        to.getId(),
                        to.getEntityType().name(),
                        relationType,
                        typeGroup.name());
        return service.submit(() -> relationRepository.findOne(key) != null);
    }

    @Override
    public ListenableFuture<EntityRelation> getRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) {
        RelationCompositeKey key =
                new RelationCompositeKey(from.getId(),
                        from.getEntityType().name(),
                        to.getId(),
                        to.getEntityType().name(),
                        relationType,
                        typeGroup.name());
        return service.submit(() -> DaoUtil.getData(relationRepository.findOne(key)));
    }

    @Override
    public ListenableFuture<Boolean> saveRelation(EntityRelation relation) {
        return service.submit(() -> relationRepository.save(new RelationEntity(relation)) != null);
    }

    @Override
    public ListenableFuture<Boolean> deleteRelation(EntityRelation relation) {
        RelationCompositeKey key = new RelationCompositeKey(relation);
        return service.submit(
                () -> {
                    boolean relationExistsBeforeDelete = relationRepository.exists(key);
                    relationRepository.delete(key);
                    return relationExistsBeforeDelete;
                });
    }

    @Override
    public ListenableFuture<Boolean> deleteRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) {
        RelationCompositeKey key =
                new RelationCompositeKey(from.getId(),
                        from.getEntityType().name(),
                        to.getId(),
                        to.getEntityType().name(),
                        relationType,
                        typeGroup.name());
        return service.submit(
                () -> {
                    boolean relationExistsBeforeDelete = relationRepository.exists(key);
                    relationRepository.delete(key);
                    return relationExistsBeforeDelete;
                });
    }

    @Override
    public ListenableFuture<Boolean> deleteOutboundRelations(EntityId entity) {
        return service.submit(
                () -> {
                    boolean relationExistsBeforeDelete = relationRepository
                            .findAllByFromIdAndFromType(entity.getId(), entity.getEntityType().name())
                            .size() > 0;
                    relationRepository.deleteByFromIdAndFromType(entity.getId(), entity.getEntityType().name());
                    return relationExistsBeforeDelete;
                });
    }

    @Override
    public ListenableFuture<List<EntityRelation>> findRelations(EntityId from, String relationType, RelationTypeGroup typeGroup, EntityType childType, TimePageLink pageLink) {
        Specification<RelationEntity> timeSearchSpec = JpaAbstractSearchTimeDao.<RelationEntity>getTimeSearchPageSpec(pageLink, "toId");
        Specification<RelationEntity> fieldsSpec = getEntityFieldsSpec(from, relationType, typeGroup, childType);
        Pageable pageable = new PageRequest(0, pageLink.getLimit(),
                new Sort(
                        new Order(ASC, "relationTypeGroup"),
                        new Order(ASC, "relationType"),
                        new Order(ASC, "toType"))
        );
        return service.submit(() ->
                DaoUtil.convertDataList(relationRepository.findAll(where(timeSearchSpec).and(fieldsSpec), pageable).getContent()));
    }

    private Specification<RelationEntity> getEntityFieldsSpec(EntityId from, String relationType, RelationTypeGroup typeGroup, EntityType childType) {
        return new Specification<RelationEntity>() {
            @Override
            public Predicate toPredicate(Root<RelationEntity> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicates = new ArrayList<>();
                if (from != null) {
                    Predicate fromIdPredicate = criteriaBuilder.equal(root.get("fromId"), from.getId());
                    predicates.add(fromIdPredicate);
                    Predicate fromEntityTypePredicate = criteriaBuilder.equal(root.get("fromType"), from.getEntityType().name());
                    predicates.add(fromEntityTypePredicate);
                }
                if (relationType != null) {
                    Predicate relationTypePredicate = criteriaBuilder.equal(root.get("relationType"), relationType);
                    predicates.add(relationTypePredicate);
                }
                if (typeGroup != null) {
                    Predicate typeGroupPredicate = criteriaBuilder.equal(root.get("relationTypeGroup"), typeGroup.name());
                    predicates.add(typeGroupPredicate);
                }
                if (childType != null) {
                    Predicate childTypePredicate = criteriaBuilder.equal(root.get("toType"), childType.name());
                    predicates.add(childTypePredicate);
                }
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
        };
    }
}
