package net.sf.jxls.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.parser.ASTIdentifier;
import org.apache.commons.jexl2.parser.ASTReference;
import org.apache.commons.jexl2.parser.Node;
import org.apache.commons.jexl2.parser.Parser;
import org.apache.commons.jexl2.parser.SimpleNode;

public class ExpressionCollectionParser {

    public final static String COLLECTION_REFERENCE_SUFFIX = "_JxLsC_";

    // This is set up as a ThreadLocal parser to avoid threading issues.
    private static ThreadLocal parser = new ThreadLocal() {
        protected synchronized Object initialValue() {
            return new Parser(new StringReader(";"));
        }
    };

    private boolean jexlInnerCollectionsAccess;

    private String collectionExpression;
    private Collection collection;

    public ExpressionCollectionParser(JexlContext jexlContext, String expr, boolean jexlInnerCollectionsAccess) {
        try {
            this.jexlInnerCollectionsAccess = jexlInnerCollectionsAccess;
            SimpleNode tree = ((Parser) parser.get()).parse(new StringReader(expr), null);
            ArrayList references = new ArrayList();
            findReferences(references, tree);
            findCollection(jexlContext, references);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCollectionExpression() {
        return collectionExpression;
    }

    public Collection getCollection() {
        return collection;
    }

    private void findReferences(List references, Node node) {

        if (node instanceof ASTReference) {
            references.add(node);
        }

        int childCount = node.jjtGetNumChildren();
        for (int i = 0; i < childCount; i++) {
            findReferences(references, node.jjtGetChild(i));
        }
    }

    private void findCollection(JexlContext jexlContext, List references) {

        Node node;

        for (Iterator itr = references.iterator(); itr.hasNext();) {
            node = (Node) itr.next();
            String newExpression = findCollectionProperties(jexlContext, node);
            if (newExpression != null) {
                if (!newExpression.endsWith(COLLECTION_REFERENCE_SUFFIX)) {
                    this.collectionExpression = newExpression;
                }
                break;
            }
        }
    }

    private String findCollectionProperties(JexlContext jexlContext, Node node) {

        int childCount = node.jjtGetNumChildren();
        Node child;
        String subExpr = null;

        JexlEngine jexlEngine = new JexlEngine();
        for (int i = 0; i < childCount; i++) {
            child = node.jjtGetChild(i);
            if (child instanceof ASTIdentifier) {
                ASTIdentifier ident = (ASTIdentifier) child;
                if (subExpr == null) {
                    subExpr = ident.image;
                } else {
                    subExpr = subExpr + "." + ident.image;
                }
                if (jexlInnerCollectionsAccess) {
                    if (subExpr.endsWith(COLLECTION_REFERENCE_SUFFIX)) {
                        return subExpr;
                    }
                }
                try {
                    Expression e = jexlEngine.createExpression(subExpr);
                    Object obj = e.evaluate(jexlContext);
                    if (obj instanceof Collection) {
                        this.collection = (Collection) obj;
                        return subExpr;
                    }
                } catch (Exception e) {
                    // TODO: insert proper logging here
                    return null;
                }
            }
        }

        return null;
    }
}