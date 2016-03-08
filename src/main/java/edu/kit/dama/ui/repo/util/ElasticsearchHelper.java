/*
 * Copyright 2014 Karlsruhe Institute of Technology.
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
package edu.kit.dama.ui.repo.util;

import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.base.UserData;
import static edu.kit.dama.mdm.content.impl.DublinCoreMetadataExtractor.ISO_8601_DATE_FORMAT;
import edu.kit.dama.util.DataManagerSettings;
import java.text.SimpleDateFormat;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.JSONException;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class which allows to add/remove a Dublin Core representation of a
 * digital object to/from an elasticsearch index configured by the default
 * DataManagerSettings of KIT Data Manager.
 *
 * @author mf6319
 */
public final class ElasticsearchHelper {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHelper.class);
    public final static String ELASTICSEARCH_TYPE = "dc";

    /**
     * Hidden constructor.
     */
    private ElasticsearchHelper() {
    }

    /**
     * Index the provided digital object to the elasticsearch index.
     */
    public static void indexEntry(DigitalObject pEntry) {
        LOGGER.debug("Initializing elasticsearch connection.");
        String cluster = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_CLUSTER_ID, "KITDataManager");
        String index = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_INDEX_ID, ELASTICSEARCH_TYPE);
        String hostname = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_HOST_ID, "localhost");
        int port = DataManagerSettings.getSingleton().getIntProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_PORT_ID, 9300);

        LOGGER.info("Intitializing transport client.");
        Settings esSettings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build();
        try {
            try (Client client = new TransportClient(esSettings).addTransportAddress(new InetSocketTransportAddress(hostname, port))) {
                LOGGER.info("Indexing digital object entry.");
                IndexResponse response = client.prepareIndex(index,
                        ELASTICSEARCH_TYPE,
                        pEntry.getDigitalObjectIdentifier() + "_" + ELASTICSEARCH_TYPE)
                        .setSource(entryToJson(pEntry))
                        .execute()
                        .actionGet();
                LOGGER.info("Digital Object with identifier {} was {}. Current version: {}", response.getId(), (response.isCreated()) ? "created" : "updated", response.getVersion());
            }
        } catch (JSONException ex) {
            LOGGER.error("Failed to convert entry to JSON.", ex);
        }
    }

    public static void unindexEntry(DigitalObject pEntry) {
        LOGGER.debug("Initializing elasticsearch connection.");
        String cluster = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_CLUSTER_ID, "KITDataManager");
        String index = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_INDEX_ID, ELASTICSEARCH_TYPE);
        String hostname = DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_HOST_ID, "localhost");
        int port = DataManagerSettings.getSingleton().getIntProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_PORT_ID, 9300);

        LOGGER.debug("Intitializing transport client.");

        Settings esSettings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build();

        try (Client client = new TransportClient(esSettings)
                .addTransportAddress(new InetSocketTransportAddress(hostname, port))) {
            DeleteResponse response = client.delete(new DeleteRequest(index, ELASTICSEARCH_TYPE, pEntry.getDigitalObjectIdentifier() + "_" + ELASTICSEARCH_TYPE)).actionGet();
            LOGGER.debug("Digital object with identifier {} was deleted.", response.getId());
        }
    }

    /**
     * Generate a DublinCore Json representation of the provided entry. The
     * implementation was copied from
     * <i>edu.kit.dama.mdm.content.impl.DublinCoreMetadataExtractor</i>.
     *
     * @param pEntry The entry to convert.
     *
     * @return The DublinCore Json string.
     */
    private static String entryToJson(DigitalObject pEntry) throws JSONException {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<oai_dc:dc \n"
                + "     xmlns:oai_dc=\"http://www.openarchives.org/OAI/2.0/oai_dc/\" \n"
                + "     xmlns:dc=\"http://purl.org/dc/elements/1.1/\" \n"
                + "     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n"
                + "     xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai_dc/ \n"
                + "     http://www.openarchives.org/OAI/2.0/oai_dc.xsd\">");
        xmlBuilder.append("<dc:title>").append(pEntry.getLabel()).append("</dc:title>");
        UserData uploader = pEntry.getUploader();
        if (uploader != null) {
            xmlBuilder.append("<dc:creator>").append(uploader.getFullname()).append("</dc:creator>");
            xmlBuilder.append("<dc:publisher>").append(uploader.getFullname()).append("</dc:publisher>");
        }

        for (UserData experimenter : pEntry.getExperimenters()) {
            //don't list uploader a second time here
            if (uploader == null || !experimenter.equals(uploader)) {
                xmlBuilder.append("<dc:contributor>").append(experimenter.getFullname()).append("</dc:contributor>");
            }
        }

        if (pEntry.getInvestigation() != null) {
            xmlBuilder.append("<dc:subject>").append(pEntry.getInvestigation().getTopic()).append("</dc:subject>");
            String description = pEntry.getInvestigation().getDescription();
            if (description != null) {
                xmlBuilder.append("<dc:description>").append(description).append("</dc:description>");
            }
            if (pEntry.getInvestigation().getStudy() != null) {
                String legalNotes = pEntry.getInvestigation().getStudy().getLegalNote();
                if (legalNotes != null) {
                    xmlBuilder.append("<dc:rights>").append(legalNotes).append("</dc:rights>");
                }
            }
        }
        if (pEntry.getStartDate() != null) {
            xmlBuilder.append("<dc:date>").append(new SimpleDateFormat(ISO_8601_DATE_FORMAT).format(pEntry.getStartDate())).append("</dc:date>");
        }
        //not possible in our case - use binary type 'application/octet-stream'
        xmlBuilder.append("<dc:format>").append("application/octet-stream").append("</dc:format>");

        //see http://dublincore.org/documents/2012/06/14/dcmi-terms/?v=dcmitype  
        xmlBuilder.append("<dc:type>").append("Dataset").append("</dc:type>");
        xmlBuilder.append("<dc:identifier>").append(pEntry.getDigitalObjectId().getStringRepresentation()).append("</dc:identifier>");

        //>>>not possible, yet -> later: getDigitalObject().getPredecessor() for processing results!?
        //xmlBuilder.append("<dc:source>").append(----).append("</dc:source>");
        //xmlBuilder.append("<dc:relation>").append(----).append("</dc:relation>");
        //>>>not relevant!? Otherwise refer to RFC 4646
        //xmlBuilder.append("<dc:language>").append("").append("</dc:language>");
        //Not relevant?!
        //xmlBuilder.append("<dc:coverage>").append("").append("</dc:coverage>");
        xmlBuilder.append("</oai_dc:dc>");

        return XML.toJSONObject(xmlBuilder.toString()).toString();
    }

}
