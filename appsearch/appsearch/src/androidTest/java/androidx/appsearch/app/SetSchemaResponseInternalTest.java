/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appsearch.app;

import static com.google.common.truth.Truth.assertThat;

import androidx.appsearch.app.AppSearchSchema.PropertyConfig;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;

import org.junit.Test;

import java.util.List;

/** Tests for private APIs of {@link SetSchemaResponse}. */
public class SetSchemaResponseInternalTest {
    @Test
    public void testRebuild() {
        SetSchemaResponse.MigrationFailure failure1 = new SetSchemaResponse.MigrationFailure(
                "namespace",
                "failure1",
                "schemaType",
                AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR, "errorMessage"));
        SetSchemaResponse.MigrationFailure failure2 = new SetSchemaResponse.MigrationFailure(
                "namespace",
                "failure2",
                "schemaType",
                AppSearchResult.newFailedResult(
                        AppSearchResult.RESULT_INTERNAL_ERROR,  "errorMessage"));

        SetSchemaResponse original = new SetSchemaResponse.Builder()
                .addDeletedType("delete1")
                .addIncompatibleType("incompatible1")
                .addMigratedType("migrated1")
                .addMigrationFailure(failure1)
                .build();
        assertThat(original.getDeletedTypes()).containsExactly("delete1");
        assertThat(original.getIncompatibleTypes()).containsExactly("incompatible1");
        assertThat(original.getMigratedTypes()).containsExactly("migrated1");
        assertThat(original.getMigrationFailures()).containsExactly(failure1);

        SetSchemaResponse rebuild = new SetSchemaResponse.Builder(original)
                        .addDeletedType("delete2")
                        .addIncompatibleType("incompatible2")
                        .addMigratedType("migrated2")
                        .addMigrationFailure(failure2)
                        .build();

        // rebuild won't effect the original object
        assertThat(original.getDeletedTypes()).containsExactly("delete1");
        assertThat(original.getIncompatibleTypes()).containsExactly("incompatible1");
        assertThat(original.getMigratedTypes()).containsExactly("migrated1");
        assertThat(original.getMigrationFailures()).containsExactly(failure1);

        assertThat(rebuild.getDeletedTypes()).containsExactly("delete1", "delete2");
        assertThat(rebuild.getIncompatibleTypes()).containsExactly("incompatible1",
                "incompatible2");
        assertThat(rebuild.getMigratedTypes()).containsExactly("migrated1", "migrated2");
        assertThat(rebuild.getMigrationFailures()).containsExactly(failure1, failure2);
    }

    // TODO(b/268521214): Move test to cts once deletion propagation is available in framework.
    @Test
    public void testPropertyConfig_deletionPropagation() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Test")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("qualifiedId1")
                        .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                        .setJoinableValueType(StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                        .build())
                .build();

        assertThat(schema.getSchemaType()).isEqualTo("Test");
        List<PropertyConfig> properties = schema.getProperties();
        assertThat(properties).hasSize(1);

        assertThat(properties.get(0).getName()).isEqualTo("qualifiedId1");
        assertThat(properties.get(0).getCardinality())
                .isEqualTo(PropertyConfig.CARDINALITY_OPTIONAL);
        assertThat(((StringPropertyConfig) properties.get(0)).getJoinableValueType())
                .isEqualTo(StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID);
    }
}
