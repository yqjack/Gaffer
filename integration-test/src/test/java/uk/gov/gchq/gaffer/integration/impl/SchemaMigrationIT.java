/*
 * Copyright 2018 Crown Copyright
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

package uk.gov.gchq.gaffer.integration.impl;

import org.junit.Before;
import org.junit.Test;

import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.Entity;
import uk.gov.gchq.gaffer.data.element.Entity.Builder;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.element.function.ElementTransformer;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition;
import uk.gov.gchq.gaffer.data.util.ElementUtil;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.graph.GraphConfig;
import uk.gov.gchq.gaffer.graph.hook.migrate.MigrateElement;
import uk.gov.gchq.gaffer.graph.hook.migrate.SchemaMigration;
import uk.gov.gchq.gaffer.graph.hook.migrate.SchemaMigration.MigrationOutputType;
import uk.gov.gchq.gaffer.integration.AbstractStoreIT;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.function.migration.ToInteger;
import uk.gov.gchq.gaffer.operation.function.migration.ToLong;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.store.schema.SchemaEdgeDefinition;
import uk.gov.gchq.gaffer.store.schema.SchemaEntityDefinition;
import uk.gov.gchq.gaffer.store.schema.TypeDefinition;
import uk.gov.gchq.gaffer.user.User;
import uk.gov.gchq.koryphe.impl.binaryoperator.Sum;
import uk.gov.gchq.koryphe.impl.predicate.IsMoreThan;

import java.util.Arrays;
import java.util.Collections;

public class SchemaMigrationIT extends AbstractStoreIT {

    public static final Entity ENTITY_OLD = new Builder()
            .group("entityOld")
            .vertex("oldVertex")
            .property("count", 10)
            .build();
    public static final Entity ENTITY_OLD_MIGRATED_TO_NEW = new Builder()
            .group("entityNew")
            .vertex("oldVertex")
            .property("count", 10L)
            .build();
    public static final Entity ENTITY_NEW = new Builder()
            .group("entityNew")
            .vertex("newVertex")
            .property("count", 10L)
            .build();
    public static final Entity ENTITY_NEW_MIGRATED_TO_OLD = new Builder()
            .group("entityOld")
            .vertex("newVertex")
            .property("count", 10)
            .build();
    public static final Edge EDGE_OLD = new Edge.Builder()
            .group("edgeOld")
            .source("oldVertex")
            .dest("oldVertex2")
            .directed(true)
            .property("count", 10)
            .build();
    public static final Edge EDGE_OLD_MIGRATED_TO_NEW = new Edge.Builder()
            .group("edgeNew")
            .source("oldVertex")
            .dest("oldVertex2")
            .directed(true)
            .property("count", 10L)
            .build();
    public static final Edge EDGE_NEW = new Edge.Builder()
            .group("edgeNew")
            .source("newVertex")
            .dest("newVertex2")
            .directed(true)
            .property("count", 10L)
            .build();
    public static final Edge EDGE_NEW_MIGRATED_TO_OLD = new Edge.Builder()
            .group("edgeOld")
            .source("newVertex")
            .dest("newVertex2")
            .directed(true)
            .property("count", 10)
            .build();
    public static final View OLD_ENTITY_VIEW = new View.Builder()
            .entity("entityOld", new ViewElementDefinition.Builder()
                    .preAggregationFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(1))
                            .build())
                    .postTransformFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(2))
                            .build())
                    .build())
            .build();

    public static final View OLD_EDGE_VIEW = new View.Builder()
            .edge("edgeOld", new ViewElementDefinition.Builder()
                    .preAggregationFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(1))
                            .build())
                    .postTransformFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(2))
                            .build())
                    .build())
            .build();


    public static final View NEW_ENTITY_VIEW = new View.Builder()
            .entity("entityNew", new ViewElementDefinition.Builder()
                    .preAggregationFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(1L))
                            .build())
                    .postTransformFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(2L))
                            .build())
                    .build())
            .build();

    public static final View NEW_EDGE_VIEW = new View.Builder()
            .edge("edgeNew", new ViewElementDefinition.Builder()
                    .preAggregationFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(1L))
                            .build())
                    .postTransformFilter(new ElementFilter.Builder()
                            .select("count")
                            .execute(new IsMoreThan(2L))
                            .build())
                    .build())
            .build();


    public static final View OLD_VIEW = new View.Builder()
            .merge(OLD_ENTITY_VIEW)
            .merge(OLD_EDGE_VIEW)
            .build();

    public static final View NEW_VIEW = new View.Builder()
            .merge(NEW_ENTITY_VIEW)
            .merge(NEW_EDGE_VIEW)
            .build();

    public static final View FULL_VIEW = new View.Builder()
            .merge(OLD_VIEW)
            .merge(NEW_VIEW)
            .build();


    private SchemaMigration migration;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        addDefaultElements();
    }

    protected Graph.Builder getGraphBuilder() {
        migration = createMigration();
        return super.getGraphBuilder()
                .config(new GraphConfig.Builder()
                        .graphId("graph1")
                        .addHook(migration)
                        .build());
    }

    @Override
    protected Schema createSchema() {
        return new Schema.Builder()
                .entity("entityOld", new SchemaEntityDefinition.Builder()
                        .vertex("string")
                        .property("count", "int")
                        .build())
                .entity("entityNew", new SchemaEntityDefinition.Builder()
                        .vertex("string")
                        .property("count", "long")
                        .build())
                .edge("edgeOld", new SchemaEdgeDefinition.Builder()
                        .source("string")
                        .destination("string")
                        .directed("either")
                        .property("count", "int")
                        .build())
                .edge("edgeNew", new SchemaEdgeDefinition.Builder()
                        .source("string")
                        .destination("string")
                        .directed("either")
                        .property("count", "long")
                        .build())
                .type("string", String.class)
                .type("either", Boolean.class)
                .type("int", new TypeDefinition.Builder()
                        .clazz(Integer.class)
                        .aggregateFunction(new Sum())
                        .build())
                .type("long", new TypeDefinition.Builder()
                        .clazz(Long.class)
                        .aggregateFunction(new Sum())
                        .build())
                .build();
    }

    @Override
    public void addDefaultElements() throws OperationException {
        graph.execute(new AddElements.Builder()
                .input(ENTITY_OLD, ENTITY_NEW, EDGE_OLD, EDGE_NEW)
                .build(), new User());
    }


    //--- Output NEW ---

    @Test
    public void shouldMigrateOldToNew() throws OperationException {
        migration.setOutputType(MigrationOutputType.NEW);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(OLD_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD_MIGRATED_TO_NEW,
                        ENTITY_NEW,
                        EDGE_OLD_MIGRATED_TO_NEW,
                        EDGE_NEW
                ),
                results);
    }

    @Test
    public void shouldMigrateNewToNew() throws OperationException {
        migration.setOutputType(MigrationOutputType.NEW);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(NEW_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD_MIGRATED_TO_NEW,
                        ENTITY_NEW,
                        EDGE_OLD_MIGRATED_TO_NEW,
                        EDGE_NEW
                ),
                results);
    }

    @Test
    public void shouldMigrateOldAndNewToNew() throws OperationException {
        migration.setOutputType(MigrationOutputType.NEW);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(FULL_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD_MIGRATED_TO_NEW,
                        ENTITY_NEW,
                        EDGE_OLD_MIGRATED_TO_NEW,
                        EDGE_NEW
                ),
                results);
    }

    //--- Output OLD ---

    @Test
    public void shouldMigrateOldToOld() throws OperationException {
        migration.setOutputType(MigrationOutputType.OLD);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(OLD_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD,
                        ENTITY_NEW_MIGRATED_TO_OLD,
                        EDGE_OLD,
                        EDGE_NEW_MIGRATED_TO_OLD
                ),
                results);
    }

    @Test
    public void shouldMigrateNewToOld() throws OperationException {
        migration.setOutputType(MigrationOutputType.OLD);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(NEW_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD,
                        ENTITY_NEW_MIGRATED_TO_OLD,
                        EDGE_OLD,
                        EDGE_NEW_MIGRATED_TO_OLD
                ),
                results);
    }

    @Test
    public void shouldMigrateOldAndNewToOld() throws OperationException {
        migration.setOutputType(MigrationOutputType.OLD);

        // When
        final CloseableIterable<? extends Element> results = graph.execute(
                new GetElements.Builder()
                        .input("oldVertex", "newVertex")
                        .view(FULL_VIEW)
                        .build(),
                new User());

        // Then
        ElementUtil.assertElementEquals(
                Arrays.asList(
                        ENTITY_OLD,
                        ENTITY_NEW_MIGRATED_TO_OLD,
                        EDGE_OLD,
                        EDGE_NEW_MIGRATED_TO_OLD
                ),
                results);
    }

    private SchemaMigration createMigration() {
        final SchemaMigration migration = new SchemaMigration();

        migration.setEntities(Collections.singletonList(
                new MigrateElement(
                        "entityOld",
                        "entityNew",
                        new ElementTransformer.Builder()
                                .select("count")
                                .execute(new ToLong())
                                .project("count")
                                .build(),
                        new ElementTransformer.Builder()
                                .select("count")
                                .execute(new ToInteger())
                                .project("count")
                                .build()
                )
        ));
        migration.setEdges(Collections.singletonList(
                new MigrateElement(
                        "edgeOld",
                        "edgeNew",
                        new ElementTransformer.Builder()
                                .select("count")
                                .execute(new ToLong())
                                .project("count")
                                .build(),
                        new ElementTransformer.Builder()
                                .select("count")
                                .execute(new ToInteger())
                                .project("count")
                                .build()
                )
        ));
        return migration;
    }
}
