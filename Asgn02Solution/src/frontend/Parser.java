/**
 * Parser class for a simple interpreter.
 * 
 * (c) 2024 by Ronald Mak
 * Department of Computer Science
 * San Jose State University
 */
package frontend;

import java.nio.channels.SelectableChannel;
import java.util.HashSet;

import intermediate.*;
import static frontend.Token.TokenType.*;
import static intermediate.Node.NodeType.*;
import static intermediate.Node.NodeType.CASE;
import static intermediate.Node.NodeType.IF;
import static intermediate.Node.NodeType.NOT;
import static intermediate.Node.NodeType.WHILE;
import static intermediate.Node.NodeType.FOR;

public class Parser
{
    private Scanner scanner;
    private Symtab symtab;
    private Token currentToken;
    private int lineNumber;
    private int errorCount;
    
    public Parser(Scanner scanner, Symtab symtab)
    {
        this.scanner = scanner;
        this.symtab  = symtab;
        this.currentToken = null;
        this.lineNumber = 1;
        this.errorCount = 0;
    }
    
    public int errorCount() { return errorCount; }
    
    public Node parseProgram()
    {
        Node programNode = new Node(Node.NodeType.PROGRAM);
        
        // First token!
        currentToken = scanner.nextToken();
        
        if (currentToken.type == Token.TokenType.PROGRAM) 
        {
            // Consume PROGRAM.
            currentToken = scanner.nextToken();  
        }
        else syntaxError("Expecting PROGRAM");
        
        if (currentToken.type == IDENTIFIER) 
        {
            String programName = currentToken.text;
            symtab.enter(programName);
            programNode.text = programName;
            
            // Consume program name.
            currentToken = scanner.nextToken();
        }
        else syntaxError("Expecting program name");
        
        if (currentToken.type == SEMICOLON) 
        {
            // Consume ;
            currentToken = scanner.nextToken();
        }
        else syntaxError("Missing ;");
        
        if (currentToken.type != BEGIN) syntaxError("Expecting BEGIN");
        
        // The PROGRAM node adopts the COMPOUND tree.
        programNode.adopt(parseCompoundStatement());
        if (currentToken.type == SEMICOLON) {
            //iunno its prob fine
        }
        else if (currentToken.type != PERIOD) syntaxError("Expecting .");
        return programNode;
    }
    
    private static HashSet<Token.TokenType> statementStarters;
    private static HashSet<Token.TokenType> statementFollowers;
    private static HashSet<Token.TokenType> relationalOperators;
    private static HashSet<Token.TokenType> simpleExpressionOperators;
    private static HashSet<Token.TokenType> termOperators;

    static
    {
        statementStarters = new HashSet<Token.TokenType>();
        statementFollowers = new HashSet<Token.TokenType>();
        relationalOperators = new HashSet<Token.TokenType>();
        simpleExpressionOperators = new HashSet<Token.TokenType>();
        termOperators = new HashSet<Token.TokenType>();
        
        // Tokens that can start a statement.
        statementStarters.add(BEGIN);
        statementStarters.add(IDENTIFIER);
        statementStarters.add(REPEAT);
        statementStarters.add(Token.TokenType.IF);
        statementStarters.add(Token.TokenType.WHILE);
        statementStarters.add(Token.TokenType.FOR);
        statementStarters.add(Token.TokenType.CASE);
        
        // Tokens that can immediately follow a statement.
        statementFollowers.add(SEMICOLON);
        statementFollowers.add(END);
        statementFollowers.add(UNTIL);
        statementFollowers.add(END_OF_FILE);
        statementFollowers.add(THEN);
        statementFollowers.add(ELSE);
        statementFollowers.add(DO);
        statementFollowers.add(TO);
        statementFollowers.add(DOWNTO);
        statementFollowers.add(OF);
        
        relationalOperators.add(EQUALS);
        relationalOperators.add(NOT_EQUALS);
        relationalOperators.add(LESS_THAN);
        relationalOperators.add(GREATER_THAN);
        relationalOperators.add(LESS_EQUALS);
        relationalOperators.add(GREATER_EQUALS);

        
        simpleExpressionOperators.add(PLUS);
        simpleExpressionOperators.add(MINUS);
        simpleExpressionOperators.add(DIV);
        
        termOperators.add(STAR);
        termOperators.add(SLASH);
    }
    
    private Node parseStatement()
    {
        Node stmtNode = null;
        int savedLineNumber = currentToken.lineNumber;
        lineNumber = savedLineNumber;
        switch (currentToken.type)
        {
            case IDENTIFIER : 
            {
                String text = currentToken.getText().toLowerCase();
                
                stmtNode =   
                      (text.equals("write"))   ? parseWriteStatement()
                    : (text.equals("writeln")) ? parseWritelnStatement()
                    :                            parseAssignmentStatement();
                break;
            }
            case BEGIN  : stmtNode = parseCompoundStatement(); break;
            case REPEAT : stmtNode = parseRepeatStatement();   break;

            case IF : stmtNode = parseIfStatement(); break;
            
            case WHILE : stmtNode = parseWhileStatement(); break;
            
            case FOR : stmtNode = parseForStatement(); break;

            case CASE : stmtNode = parseCaseStatement(); break;

            // Empty statement.
            case SEMICOLON : stmtNode = null; break;  
            
            default : syntaxError("Unexpected token");
        }
        
        if (stmtNode != null) stmtNode.lineNumber = savedLineNumber;
        return stmtNode;
    }
    
private Node parseAssignmentStatement()
{
    // The current token should now be the left-hand-side variable name.
    
    Node assignmentNode = new Node(ASSIGN);
    
    // Enter the variable name into the symbol table
    // if it isn't already in there.
    String variableName = currentToken.text;
    SymtabEntry variableEntry = symtab.lookup(variableName.toLowerCase());
    if (variableEntry == null) 
    {
        variableEntry = symtab.enter(variableName.toLowerCase());
    }
    
    // The assignment node adopts the variable node 
    // as its first child.
    Node lhsNode = new Node(VARIABLE);        
    lhsNode.text  = variableName;
    lhsNode.entry = variableEntry;
    assignmentNode.adopt(lhsNode);
    
    // Consume the LHS variable.
    currentToken = scanner.nextToken();  
    
    if (currentToken.type == COLON_EQUALS) 
    {
        // Consume :=
        currentToken = scanner.nextToken();  
    }
    else syntaxError("Missing :=");
    
    // The assignment node adopts the expression node
    // as its second child.
    Node rhsNode = parseExpression(containsNot());
    assignmentNode.adopt(rhsNode);
    
    return assignmentNode;
}
    
    private Node parseCompoundStatement()
    {
        Node compoundNode = new Node(COMPOUND);
        compoundNode.lineNumber = currentToken.lineNumber;
        
        // Consume BEGIN
        currentToken = scanner.nextToken();
        
        parseStatementList(compoundNode, END);    
        
        if (currentToken.type == END) 
        {
            // Consume END
            currentToken = scanner.nextToken();  
        }
        else syntaxError("Expecting END");
        
        return compoundNode;
    }

    private void parseStatementList(Node parentNode, 
                                    Token.TokenType terminalType)
    {
        while (   (currentToken.type != terminalType) 
               && (currentToken.type != END_OF_FILE))
        {
            Node stmtNode = parseStatement();
            if (stmtNode != null) parentNode.adopt(stmtNode);
            
            // A semicolon separates statements.
            if (currentToken.type == SEMICOLON)
            {
                while (currentToken.type == SEMICOLON)
                {
                    // Consume ;
                    currentToken = scanner.nextToken();  
                }
            }
            else if (statementStarters.contains(currentToken.type))
            {
                syntaxError("Missing ;");
            }
        }
    }

    private boolean containsNot() { //if has not, will increment also, kinda also cursed but is to avoid changing more case statements
        System.out.println("Here at " + currentToken.lineNumber);
        boolean containsNot = false;
        if (currentToken.type == Token.TokenType.NOT) {
            containsNot = true;
            currentToken = scanner.nextToken();
        }
        return containsNot;
    }


    private Node parseIfStatement() { ////////////////////////////////////////////////////////////////////////////
        Node ifNode = new Node(IF);
        ifNode.lineNumber = currentToken.lineNumber;
        currentToken = scanner.nextToken();
        ifNode.adopt(parseExpression(containsNot()));
            if (currentToken.type == THEN) {
                currentToken = scanner.nextToken();
                ifNode.adopt(parseStatement());
            }
            else {
                syntaxError("Expecting THEN");
            }

        if (currentToken.type == ELSE) {
            currentToken = scanner.nextToken();
            ifNode.adopt(parseStatement());
        }
        return ifNode;
    }

    private Node parseCaseStatement() {
        Node caseNode = new Node(SELECT);//??
        caseNode.lineNumber = currentToken.lineNumber;
        currentToken = scanner.nextToken();
        caseNode.adopt(parseExpression(containsNot()));
        if (currentToken.type == OF) {
            currentToken = scanner.nextToken();
            while(currentToken.type != END) {
                Node selectNode = new Node(SELECT_BRANCH);
                selectNode.adopt(parseConstants());// need to account for multiple
                currentToken = scanner.nextToken(); //skip :
                selectNode.adopt(parseStatement());
                currentToken = scanner.nextToken(); //skip ;
                caseNode.adopt(selectNode);
            }
        }
        else {
            syntaxError("Expecting OF");
        }
        return caseNode;
    }

    private Node parseConstants() { //plural
        Node selectConstants = new Node(SELECT_CONSTANTS);
        selectConstants.adopt(parseConstant());
        while (currentToken.type == COMMA) {
            currentToken = scanner.nextToken();
            selectConstants.adopt(parseConstant());
        }
        return selectConstants;
    }

    private Node parseConstant() {
        switch (currentToken.type) {
            case INTEGER -> parseIntegerConstant(false);
            case REAL -> parseRealConstant(false);
            case STRING -> parseStringConstant();
            case MINUS -> negativesPatch();
            default -> syntaxError("Unexpected or no constant");
        }
        return new Node(UNKNOWN);
    }

    private Node negativesPatch() {  //cursed but may work
        currentToken = scanner.nextToken();
        switch (currentToken.type) {
            case INTEGER -> parseIntegerConstant(true);
            case REAL -> parseRealConstant(true);
            default -> syntaxError("Unexpected or no constant?");
        }
        return new Node(UNKNOWN);
    }

    private Node parseRepeatStatement()
    {
        // The current token should now be REPEAT.
        
        // Create a LOOP node.
        Node loopNode = new Node(LOOP);
        
        // Consume REPEAT
        currentToken = scanner.nextToken();

        parseStatementList(loopNode, UNTIL);    
        
        if (currentToken.type == UNTIL) 
        {
            // Create a TEST node.
            // It adopts the test expression node.
            Node testNode = new Node(TEST);
            lineNumber = currentToken.lineNumber;
            testNode.lineNumber = lineNumber;
            
            // Consume UNTIL.
            currentToken = scanner.nextToken(); 
            
            testNode.adopt(parseExpression(containsNot()));
            
            // The LOOP node adopts the TEST node
            // as its final child.
            loopNode.adopt(testNode);
        }
        else syntaxError("Expecting UNTIL");
        
        return loopNode;
    }
    
    private Node parseWhileStatement() {
    	
    	Node whileNode = new Node(WHILE);
    	
    	whileNode.lineNumber = currentToken.lineNumber;
    	
    	currentToken = scanner.nextToken();
    	
    	whileNode.adopt(parseExpression(containsNot()));
    	
    	if (currentToken.type == DO) {
    		currentToken = scanner.nextToken();
    		whileNode.adopt(parseStatement());
    	}
    	else syntaxError("Expecting DO");
    	
    	return whileNode;
    }
    
    private Node parseForStatement() {
    	Node forNode = new Node(FOR);
    	
    	forNode.lineNumber = currentToken.lineNumber;
    	
    	currentToken = scanner.nextToken();
    	forNode.adopt(parseAssignmentStatement());
    	
    	if (currentToken.type == TO) {
    		currentToken = scanner.nextToken();
    		forNode.adopt(parseExpression(containsNot()));
    	}
    	else if (currentToken.type == DOWNTO) {
    		currentToken = scanner.nextToken();
    		forNode.adopt(parseExpression(containsNot()));
    	}
    	else {
    		syntaxError("Expecting TO or DOWNTO");
    	}
    	
    	if (currentToken.type == DO) {
    		currentToken = scanner.nextToken();
    		forNode.adopt(parseStatement());
    	}
    	else {
    		syntaxError("Expecting DO");
    	}
    	return forNode;
    	
    }
    
    private Node parseWriteStatement()
    {
        // The current token should now be WRITE.
        
        // Create a WRITE node.
        // It adopts the variable or string node.
        Node writeNode = new Node(Node.NodeType.WRITE);
        
        // Consume WRITE.
        currentToken = scanner.nextToken();  
        
        parseWriteArguments(writeNode);
        if (writeNode.children.size() == 0)
        {
            syntaxError("Invalid WRITE statement");
        }
        
        return writeNode;
    }
    
    private Node parseWritelnStatement()
    {
        // The current token should now be WRITELN.
        
        // Create a WRITELN node.
        // It adopts the variable or string node.
        Node writelnNode = new Node(Node.NodeType.WRITELN);
        
        // Consume WRITELN.
        currentToken = scanner.nextToken();  
        
        if (currentToken.type == LPAREN) parseWriteArguments(writelnNode);
        return writelnNode;
    }
    
    private void parseWriteArguments(Node node)
    {
        // The current token should now be (
        
        boolean hasArgument = false;
        
        if (currentToken.type == LPAREN) 
        {
            // Consume (
            currentToken = scanner.nextToken();
        }
        else syntaxError("Missing left parenthesis");
        
        if (currentToken.type == IDENTIFIER)
        {
            node.adopt(parseVariable());
            hasArgument = true;
        }
        else if (   (currentToken.type == CHARACTER)
                 || (currentToken.type == STRING))
        {
            node.adopt(parseStringConstant());
            hasArgument = true;
        }
        else syntaxError("Invalid WRITE or WRITELN statement");
        
        // Look for a field width and a count of decimal places.
        if (hasArgument) {
            if (currentToken.type == COLON) {
			    // Consume :
                currentToken = scanner.nextToken();
                
                if (currentToken.type == INTEGER) {
                    // Field width
                    node.adopt(parseIntegerConstant(false));
                    
                    if (currentToken.type == COLON) {
                        // Consume :
                        currentToken = scanner.nextToken();
                        
                        if (currentToken.type == INTEGER) {
                            // Count of decimal places.
                            node.adopt(parseIntegerConstant(false));
                        }
                        else if (currentToken.type == MINUS) {
                            currentToken = scanner.nextToken();
                            node.adopt(parseIntegerConstant(true));
                        }
                        else syntaxError("Invalid count of decimal places");
                    }
                }
                else if (currentToken.type == MINUS) {
                    currentToken = scanner.nextToken();

                    /////// copied from above
                    node.adopt(parseIntegerConstant(true));

                    if (currentToken.type == COLON) {
                        // Consume :
                        currentToken = scanner.nextToken();

                        if (currentToken.type == INTEGER) {
                            // Count of decimal places.
                            node.adopt(parseIntegerConstant(false));
                        }
                        else if (currentToken.type == MINUS) {
                            currentToken = scanner.nextToken();
                            node.adopt(parseIntegerConstant(true));
                        }
                        else syntaxError("Invalid count of decimal places");
                    }
                    ///////
                }
                else syntaxError("Invalid field width");
            }
        }
        
        if (currentToken.type == RPAREN) 
        {
            // Consume )
            currentToken = scanner.nextToken();
        }
        else syntaxError("Missing right parenthesis");
    }

    private Node parseExpression(boolean containsNot)
    {
        // The current token should now be an identifier or a number.
        System.out.println("Here2 at " + currentToken.lineNumber);
        // The expression's root node.
        if (currentToken.type == LPAREN) {
            currentToken = scanner.nextToken();
        }
        Node exprNode;
        if (containsNot) {
            exprNode = new Node(NOT);
        }
        else {
            exprNode = parseSimpleExpression();
        }
        
        // The current token might now be a relational operator.
        if (relationalOperators.contains(currentToken.type))
        {
            Token.TokenType tokenType = currentToken.type;
            Node opNode = tokenType == EQUALS    ?           new Node(EQ)
                        : tokenType == NOT_EQUALS?           new Node(NEQ)
                        : tokenType == LESS_THAN ?           new Node(LT)
                        : tokenType == GREATER_THAN ?        new Node(GT)
                        : tokenType == LESS_EQUALS ?         new Node(LEQ)
                        : tokenType == GREATER_EQUALS ?      new Node(GEQ)
                        : tokenType == COLON_EQUALS ?        new Node(CEQ)
                        : tokenType == Token.TokenType.NOT ? new Node(NOT)
                        :                                    null;
            
            // Consume relational operator.
            currentToken = scanner.nextToken();  
            
            // The relational operator node adopts the first simple expression
            // node as its first child and the second simple expression node
            // as its second child. Then it becomes the expression's root node.
            if (opNode != null)
            {
                opNode.adopt(exprNode);
                opNode.adopt(parseSimpleExpression());
                exprNode = opNode;
            }
        }
        
        return exprNode;
    }
    
    private Node parseSimpleExpression()
    {
        // The current token should now be an identifier or a number.
        
        // The simple expression's root node.
        Node simpExprNode = parseTerm();
        
        // Keep parsing more terms as long as the current token
        // is a + or - operator.
        while (simpleExpressionOperators.contains(currentToken.type))
        {
            Node opNode = currentToken.type == PLUS ? new Node(ADD)
                        : currentToken.type == MINUS ? new Node(SUBTRACT)
                        : currentToken.type == DIV ? new Node(INTDIV)
                        :                               null;
            // consume the operator.
            currentToken = scanner.nextToken();  

            // The add or subtract node adopts the first term node as its
            // first child and the next term node as its second child. 
            // Then it becomes the simple expression's root node.
            opNode.adopt(simpExprNode);
            opNode.adopt(parseTerm());
            simpExprNode = opNode;
        }
        
        return simpExprNode;
    }
    
    private Node parseTerm()
    {
        // The current token should now be an identifier or a number.
        
        // The term's root node.
        Node termNode = parseFactor();
        
        // Keep parsing more factor as long as the current token
        // is a * or / operator.
        while (termOperators.contains(currentToken.type))
        {
            Node opNode = currentToken.type == STAR ? new Node(MULTIPLY)
                                                    : new Node(DIVIDE);
            // Consume the operator.
            currentToken = scanner.nextToken();  

            // The multiply or dive node adopts the first factor node as its
            // as its first child and the next factor node as its second child. 
            // Then it becomes the term's root node.
            opNode.adopt(termNode);
            opNode.adopt(parseFactor());
            termNode = opNode;
        }
        
        return termNode;
    }
    
    private Node parseFactor()
    {
        boolean containsNot = false;
        // The current token should now be an identifier or a number or (
        if (currentToken.type == Token.TokenType.NOT) {
            containsNot = true;
            currentToken = scanner.nextToken();
        }
        if      (currentToken.type == IDENTIFIER) return parseVariable();
        else if (currentToken.type == INTEGER)    return parseIntegerConstant(false);
        else if (currentToken.type == REAL)       return parseRealConstant(false);

        else if (currentToken.type == MINUS) {
            currentToken = scanner.nextToken();
            if      (currentToken.type == INTEGER)    return parseIntegerConstant(true);
            else if (currentToken.type == REAL)       return parseRealConstant(true);
        }

        else if (currentToken.type == LPAREN)
        {
            // Consume (
            currentToken = scanner.nextToken(); 
            
            Node exprNode = parseExpression(containsNot);
            
            if (currentToken.type == RPAREN)
            {
                // Consume )
                currentToken = scanner.nextToken();  
            }
            else syntaxError("Expecting )");
            
            return exprNode;
        }
        
        else syntaxError("Unexpected token");
        return null;
    }
    
    private Node parseVariable()
    {
        // The current token should now be an identifier.
        
        // Has the variable been "declared"?
        String variableName = currentToken.text;
        SymtabEntry variableEntry = symtab.lookup(variableName.toLowerCase());
        if (variableEntry == null) semanticError("Undeclared identifier");
        
        Node node  = new Node(VARIABLE);
        node.text  = variableName;
        node.entry = variableEntry;
        
        // Consume the identifier.  
        currentToken = scanner.nextToken();  
        
        return node;
    }

    private Node parseIntegerConstant(boolean isNegative)
    {
        // The current token should now be a number.
        
        Node integerNode = new Node(INTEGER_CONSTANT);
        if (isNegative) {
            integerNode.value = -1 * (Long)currentToken.value;
        }
        else {
            integerNode.value = currentToken.value;
        }
        
        // Consume the number.
        currentToken = scanner.nextToken();     
        
        return integerNode;
    }

    private Node parseRealConstant(boolean isNegative)
    {
        // The current token should now be a number.
        
        Node realNode = new Node(REAL_CONSTANT);
        if (isNegative) {
            realNode.value = -1 * (Double)currentToken.value;
        }
        else {
            realNode.value = currentToken.value;
        }
        
        // Consume the number.
        currentToken = scanner.nextToken(); 
        
        return realNode;
    }
    
    private Node parseStringConstant()
    {
        // The current token should now be CHARACTER or STRING.
        
        Node stringNode = new Node(STRING_CONSTANT);
        stringNode.value = currentToken.value;
        
        // Consume the string.
        currentToken = scanner.nextToken();         
        return stringNode;
    }

    private void syntaxError(String message)
    {
        System.out.println("SYNTAX ERROR at line " + lineNumber 
                           + ": " + message + " at '" + currentToken.text + "'");
        errorCount++;
        
        // Recover by skipping the rest of the statement.
        // Skip to a statement follower token.
        while (! statementFollowers.contains(currentToken.type))
        {
            currentToken = scanner.nextToken();
        }
    }
    
    private void semanticError(String message)
    {
        System.out.println("SEMANTIC ERROR at line " + lineNumber 
                           + ": " + message + " at '" + currentToken.text + "'");
        errorCount++;
    }
}
