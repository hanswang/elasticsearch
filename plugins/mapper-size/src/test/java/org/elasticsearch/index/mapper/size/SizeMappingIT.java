/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.mapper.size;

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugin.mapper.MapperSizePlugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class SizeMappingIT extends ESIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put("plugin.types", MapperSizePlugin.class.getName())
                .build();
    }

    // issue 5053
    public void testThatUpdatingMappingShouldNotRemoveSizeMappingConfiguration() throws Exception {
        String index = "foo";
        String type = "mytype";

        XContentBuilder builder = jsonBuilder().startObject().startObject("_size").field("enabled", true).endObject().endObject();
        assertAcked(client().admin().indices().prepareCreate(index).addMapping(type, builder));

        // check mapping again
        assertSizeMappingEnabled(index, type, true);

        // update some field in the mapping
        XContentBuilder updateMappingBuilder = jsonBuilder().startObject().startObject("properties").startObject("otherField").field("type", "string").endObject().endObject();
        PutMappingResponse putMappingResponse = client().admin().indices().preparePutMapping(index).setType(type).setSource(updateMappingBuilder).get();
        assertAcked(putMappingResponse);

        // make sure size field is still in mapping
        assertSizeMappingEnabled(index, type, true);
    }

    public void testThatSizeCanBeSwitchedOnAndOff() throws Exception {
        String index = "foo";
        String type = "mytype";

        XContentBuilder builder = jsonBuilder().startObject().startObject("_size").field("enabled", true).endObject().endObject();
        assertAcked(client().admin().indices().prepareCreate(index).addMapping(type, builder));

        // check mapping again
        assertSizeMappingEnabled(index, type, true);

        // update some field in the mapping
        XContentBuilder updateMappingBuilder = jsonBuilder().startObject().startObject("_size").field("enabled", false).endObject().endObject();
        PutMappingResponse putMappingResponse = client().admin().indices().preparePutMapping(index).setType(type).setSource(updateMappingBuilder).get();
        assertAcked(putMappingResponse);

        // make sure size field is still in mapping
        assertSizeMappingEnabled(index, type, false);
    }

    private void assertSizeMappingEnabled(String index, String type, boolean enabled) throws IOException {
        String errMsg = String.format(Locale.ROOT, "Expected size field mapping to be " + (enabled ? "enabled" : "disabled") + " for %s/%s", index, type);
        GetMappingsResponse getMappingsResponse = client().admin().indices().prepareGetMappings(index).addTypes(type).get();
        Map<String, Object> mappingSource = getMappingsResponse.getMappings().get(index).get(type).getSourceAsMap();
        assertThat(errMsg, mappingSource, hasKey("_size"));
        String sizeAsString = mappingSource.get("_size").toString();
        assertThat(sizeAsString, is(notNullValue()));
        assertThat(errMsg, sizeAsString, is("{enabled=" + (enabled) + "}"));
    }

    public void testBasic() throws Exception {
        assertAcked(prepareCreate("test").addMapping("type", "_size", "enabled=true"));
        final String source = "{\"f\":10}";
        indexRandom(true,
                client().prepareIndex("test", "type", "1").setSource(source));
        GetResponse getResponse = client().prepareGet("test", "type", "1").setFields("_size").get();
        assertNotNull(getResponse.getField("_size"));
        assertEquals(source.length(), getResponse.getField("_size").getValue());
    }
}
