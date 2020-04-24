/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.data.elasticsearch.index.type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.kitodo.data.database.beans.Comment;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.elasticsearch.index.type.enums.ProcessTypeField;

/**
 * Implementation of Process Type.
 */
public class ProcessType extends BaseType<Process> {

    @Override
    Map<String, Object> getJsonObject(Process process) {
        String processBaseUri = process.getProcessBaseUri() != null ? process.getProcessBaseUri() : "";
        boolean projectActive = process.getProject() != null && process.getProject().isActive();
        int projectClientId = process.getProject() != null ? getId(process.getProject().getClient()) : 0;
        int processParentId = Objects.nonNull(process.getParent()) ? process.getParent().getId() : 0;

        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(ProcessTypeField.TITLE.getKey(), preventNull(process.getTitle()));
        jsonObject.put(ProcessTypeField.CREATION_DATE.getKey(), getFormattedDate(process.getCreationDate()));
        jsonObject.put(ProcessTypeField.WIKI_FIELD.getKey(), preventNull(process.getWikiField()));
        jsonObject.put(ProcessTypeField.SORT_HELPER_ARTICLES.getKey(), process.getSortHelperArticles());
        jsonObject.put(ProcessTypeField.SORT_HELPER_DOCSTRUCTS.getKey(), process.getSortHelperDocstructs());
        jsonObject.put(ProcessTypeField.SORT_HELPER_STATUS.getKey(), preventNull(process.getSortHelperStatus()));
        jsonObject.put(ProcessTypeField.SORT_HELPER_IMAGES.getKey(), process.getSortHelperImages());
        jsonObject.put(ProcessTypeField.SORT_HELPER_METADATA.getKey(), process.getSortHelperMetadata());
        jsonObject.put(ProcessTypeField.PROCESS_BASE_URI.getKey(), processBaseUri);
        jsonObject.put(ProcessTypeField.TEMPLATE_ID.getKey(), getId(process.getTemplate()));
        jsonObject.put(ProcessTypeField.TEMPLATE_TITLE.getKey(), getTitle(process.getTemplate()));
        jsonObject.put(ProcessTypeField.PROJECT_ID.getKey(), getId(process.getProject()));
        jsonObject.put(ProcessTypeField.PROJECT_TITLE.getKey(), getTitle(process.getProject()));
        jsonObject.put(ProcessTypeField.PROJECT_ACTIVE.getKey(), projectActive);
        jsonObject.put(ProcessTypeField.PROJECT_CLIENT_ID.getKey(), projectClientId);
        jsonObject.put(ProcessTypeField.RULESET.getKey(), getId(process.getRuleset()));
        jsonObject.put(ProcessTypeField.DOCKET.getKey(), getId(process.getDocket()));
        jsonObject.put(ProcessTypeField.BATCHES.getKey(), addObjectRelation(process.getBatches(), true));
        jsonObject.put(ProcessTypeField.COMMENTS.getKey(), addObjectRelation(process.getComments()));
        jsonObject.put(ProcessTypeField.COMMENTS_MESSAGE.getKey(), getProcessComments(process));
        jsonObject.put(ProcessTypeField.HAS_CHILDREN.getKey(), process.getChildren().size() > 0);
        jsonObject.put(ProcessTypeField.PARENT_ID.getKey(), processParentId);
        jsonObject.put(ProcessTypeField.TASKS.getKey(), addObjectRelation(process.getTasks(), true));
        jsonObject.put(ProcessTypeField.PROPERTIES.getKey(), addObjectRelation(process.getProperties(), true));
        jsonObject.put(ProcessTypeField.TEMPLATES.getKey(), addObjectRelation(process.getTemplates()));
        jsonObject.put(ProcessTypeField.WORKPIECES.getKey(), addObjectRelation(process.getWorkpieces()));
        jsonObject.put(ProcessTypeField.METADATA.getKey(), process.getMetadata());
        return jsonObject;
    }

    private String getProcessComments(Process process) {
        String commentsMessages = "";
        List<Comment> processComments = process.getComments();
        for (Comment comment : processComments) {
            commentsMessages = commentsMessages.concat(comment.getMessage());
        }
        return commentsMessages;
    }
}
