/**
 * Token class for a simple interpreter.
 * 
 * (c) 2024 by Ronald Mak
 * Department of Computer Science
 * San Jose State University
 */
package frontend;

import java.util.HashMap;

public class Token
{
    public enum TokenType
    {
        // Reserved words
        AND, ARRAY, BEGIN, CASE, CONST,
        DIV, DO, DOWNTO, ELSE, END,
        FILE, FOR, FUNCTION, GOTO, IF,
        IN, LABEL, MOD, NIL, NOT,
        OF, OR, PACKED, PROCEDURE, PROGRAM,
        RECORD, REPEAT, SET, THEN, TO,
        TYPE, UNTIL, VAR, WHILE, WITH,
        
        // Special symbols
        PERIOD, COMMA, COLON, COLON_EQUALS, SEMICOLON,
        PLUS, MINUS, STAR, SLASH, LPAREN, RPAREN, 
        EQUALS, NOT_EQUALS, LESS_THAN, LESS_EQUALS, 
        GREATER_THAN, GREATER_EQUALS, DOT_DOT, QUOTE,
        LBRACKET, RBRACKET, CARAT,
        
        IDENTIFIER, INTEGER, REAL, CHARACTER, STRING, 
        END_OF_FILE, ERROR, UNDERSCORE
    }
    
    /**
     * The table (as a hashmap) of reserved words. 
     * Initialize the table with a few words.
     */
    private static HashMap<String, TokenType> reservedWords;
    static
    {
        reservedWords = new HashMap<String, TokenType>();
        
        reservedWords.put("PROGRAM", TokenType.PROGRAM);
        reservedWords.put("BEGIN",   TokenType.BEGIN);
        reservedWords.put("END",     TokenType.END);
        reservedWords.put("REPEAT",  TokenType.REPEAT);
        reservedWords.put("UNTIL",   TokenType.UNTIL);
        reservedWords.put("VAR",     TokenType.VAR);
    }
    
    public TokenType type;       // what type of token
    public int lineNumber = 0;   // source line number of the token
    public String text = "";     // text of the token
    public Object value = null;  // the value (if any) of the token
    
    /**
     * Constructor.
     * @param firstChar the first character of the token.
     */
    private Token(char firstChar)
    {
        this.text += firstChar;
    }
    
    public String getText() { return text; }
    
    /**
     * Construct a word token.
     * @param firstChar the first character of the token.
     * @param source the input source.
     * @return the word token.
     */
    public static Token word(char firstChar, Source source)
    {
        Token token = new Token(firstChar);
        token.lineNumber = source.lineNumber();
        
        // Loop to get the rest of the characters of the word token.
        // Append letters and digits to the token.
        for (char ch = source.nextChar();
             Character.isLetterOrDigit(ch);
             ch = source.nextChar())
        {
            token.text += ch;
        }
        
        // Is it a reserved word or an identifier?
        token.type = reservedWords.get(token.text.toUpperCase());
        if (token.type == null) token.type = TokenType.IDENTIFIER;

        return token;
    }
    
    /**
     * Construct a number token and set its value.
     * @param firstChar the first character of the token.
     * @param source the input source.
     * @return the number token.
     */
    public static Token number(char firstChar, Source source)
    {
        Token token = new Token(firstChar);
        token.lineNumber = source.lineNumber();
        int pointCount = 0;
        
        // Loop to get the rest of the characters of the number token.
        // Append digits to the token.
        for (char ch = source.nextChar();
             Character.isDigit(ch) || (ch == '.');
             ch = source.nextChar())
        {
            if (ch == '.') pointCount++;
            token.text += ch;
        }
        
        // Integer constant.
        if (pointCount == 0) 
        {
            token.type  = TokenType.INTEGER;
            token.value = Long.parseLong(token.text);
        }
        
        // Real constant.
        else if (pointCount == 1) 
        {
            token.type  = TokenType.REAL;
            token.value = Double.parseDouble(token.text);
        }
        // DOT_DOT Future Encounter
        else if (pointCount == 2) {
        	token.type = TokenType.INTEGER;
        	Token sc = new Token(firstChar);
            for (char ch = source.nextChar();
                    Character.isDigit(ch);
                    ch = source.nextChar())
               {
                   sc.text += ch;
               }
        	token.value = Long.parseLong(sc.text);
        }
           
        else
        {
            token.type = TokenType.ERROR;
            tokenError(token, "Invalid number");
        }
        
        return token;
    }
    
    /**
     * Construct a string token and set its value.
     * @param firstChar the first character of the token.
     * @param source the input source.
     * @return the string token.
     */
    public static Token string(char firstChar, Source source)
    {
        // Consume the leading '
        Token token = new Token(firstChar);  
        
        token.lineNumber = source.lineNumber();

        // Loop to append the rest of the characters of the string,
        // up to but not including the closing quote.
        boolean complete = false;
        while (!complete) {
            char ch = source.nextChar();

            if (ch != '\'') {
                token.text += ch;
            }
            else { //if '' then represents the character ' and not a closing quote
                ch = source.nextChar();
                if (ch =='\'') {
                    token.text += ch;
                }
                else {
                    complete = true;
                }
            }
        }
        
        // Append the closing ' and consume it.
        token.text += '\'';
        source.nextChar();
        
        token.type = TokenType.STRING;
        
        // Don't include the leading and trailing ' in the value.
        token.value = token.text.substring(1, token.text.length() - 1);

        return token;
    }
    
    /**
     * Construct a special symbol token and set its value.
     * @param firstChar the first character of the token.
     * @param source the input source.
     * @return the special symbol token.
     */
    public static Token specialSymbol(char firstChar, Source source)
    {
        Token token = new Token(firstChar);
        token.lineNumber = source.lineNumber();

        switch (firstChar)
        {
            // One-character special symbols.
            case '.' : 
            {
            	char nextChar = source.nextChar();
            	if(nextChar == '.') {
            		token.text += '.';
            		token.type = TokenType.DOT_DOT;
            	}
            	else {
            		token.type = TokenType.PERIOD;
            		return token;
            	}
            	break;
            }
            case ',' : token.type = TokenType.COMMA;      break;
            case ';' : token.type = TokenType.SEMICOLON;  break;
            case '+' : token.type = TokenType.PLUS;       break;
            case '-' : token.type = TokenType.MINUS;      break;
            case '*' : token.type = TokenType.STAR;       break;
            case '/' : token.type = TokenType.SLASH;      break;
            case '_' : token.type = TokenType.UNDERSCORE; break;    
            case '=' : token.type = TokenType.EQUALS;     break;
            case '>' : 
            {
            	char nextChar = source.nextChar();
            	if (nextChar == '=') {
            		token.text += '=';
            		token.type = TokenType.GREATER_EQUALS;
            	}
            	else {
            		token.type = TokenType.GREATER_THAN;
            		return token;
            	}
            	break;
            }
            case '(' : token.type = TokenType.LPAREN;     break;
            case ')' : token.type = TokenType.RPAREN;     break;
            case '[' : token.type = TokenType.LBRACKET;	  break;
            case ']' : token.type = TokenType.RBRACKET;   break;
            case '^' : token.type = TokenType.CARAT;	  break;
            
            // One- or two-character special symbols.
            case ':' : 
            {
                char nextChar = source.nextChar();
                
                // Is it the := symbol?
                if (nextChar == '=') 
                {
                    token.text += '=';
                    token.type = TokenType.COLON_EQUALS;
                }
                
                // No, it's just the : symbol.
                else
                {
                    token.type = TokenType.COLON;
                    return token;  // already consumed :
                }

                break;
            }
            case '<' :
            {
            	char nextChar = source.nextChar();
            	if (nextChar == '>' ) {
            		token.text += '>';
            		token.type = TokenType.NOT_EQUALS;
            	}
            	else if (nextChar == '=') {
            		token.text += '=';
            		token.type = TokenType.LESS_EQUALS;
            	}
            	else {
            		token.type = TokenType.LESS_THAN;
            		return token;
            	}
            	break;
            }
            
            case Source.EOF : token.type = TokenType.END_OF_FILE; break;
            
            default: 
            {
                token.type = TokenType.ERROR;
                tokenError(token, "Invalid token");
            }
        }
        
        // Consume the special symbol.
        source.nextChar();
        
        return token;
    }
    
    /**
     * Handle a token error.
     * @param token the bad token.
     * @param message the error message.
     */
    private static void tokenError(Token token, String message)
    {
        System.out.println("TOKEN ERROR at line " + token.lineNumber 
                           + ": " + message + " at '" + token.text + "'");
    }
}
