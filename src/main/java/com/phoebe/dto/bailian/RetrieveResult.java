package com.phoebe.dto.bailian;

import java.util.List;

/**
 * Result from knowledge base retrieval
 */
public class RetrieveResult {

    private List<RetrieveNode> nodes;
    private String requestId;

    public RetrieveResult() {
    }

    public RetrieveResult(List<RetrieveNode> nodes, String requestId) {
        this.nodes = nodes;
        this.requestId = requestId;
    }

    public List<RetrieveNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<RetrieveNode> nodes) {
        this.nodes = nodes;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Convert retrieval results to context string for LLM
     */
    public String toContextString() {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从知识库中检索到的相关信息：\n\n");
        
        for (int i = 0; i < nodes.size(); i++) {
            RetrieveNode node = nodes.get(i);
            sb.append("【参考").append(i + 1).append("】\n");
            sb.append(node.getText());
            sb.append("\n\n");
        }
        
        return sb.toString();
    }

    /**
     * A single node/chunk from retrieval
     */
    public static class RetrieveNode {
        private String nodeId;
        private String text;
        private double score;
        private Metadata metadata;

        public RetrieveNode() {
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Metadata for a retrieval node
     */
    public static class Metadata {
        private String documentId;
        private String documentName;
        private String title;

        public Metadata() {
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}

