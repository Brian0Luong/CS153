/**
 * Token class for a simple interpreter.
 * 
 * (c) 2020 by Ronald Mak
 * Department of Computer Science
 * San Jose State University
 */
package frontend;

import java.util.HashMap;

public class Token
{
    public enum TokenType
    {
        AND, ARRAY, BEGIN, CASE, CONST,
        DIV, DO, DOWNTO, ELSE, END,
        FILE, FOR, FUNCTION, GOTO, IF,
        IN, LABEL, MOD, NIL, NOT,
        OF, OR, PACKED, PROCEDURE, PROGRAM,
        RECORD, REPEAT, SET, THEN, TO,
        TYPE, UNTIL, VAR, WHILE, WITH,
        
        PERIOD, COMMA, COLON, COLON_EQUALS, SEMICOLON,
        PLUS, MINUS, STAR, SLASH, LPAREN, RPAREN, 
        EQUALS, NOT_EQUALS, LESS_THAN, LESS_EQUALS, 
        GREATER_THAN, GREATER_EQUALS, DOT_DOT, QUOTE,
        LBRACKET, RBRACKET, CARAT,
        
        IDENTIFIER, INTEGER, REAL, CHARACTER, STRING, 
        END_OF_FILE, ERROR
    }
    
    /**
     * The table (as a hashmap) of reserved words. Initialize the table.
     */
    private static HashMap<String, TokenType> reservedWords;
    static
    {
        reservedWords = new HashMap<String, TokenType>();
        
        reservedWords.put("AND",       TokenType.AND);
        reservedWords.put("ARRAY",     TokenType.ARRAY);
        reservedWords.put("BEGIN",     TokenType.BEGIN);
        reservedWords.put("CASE",      TokenType.CASE);
        reservedWords.put("CONST",     TokenType.CONST);
        reservedWords.put("DIV",       TokenType.DIV);
        reservedWords.put("DO",        TokenType.DO);
        reservedWords.put("DOWNTO",    TokenType.DOWNTO);
        reservedWords.put("ELSE",      TokenType.ELSE);
        reservedWords.put("END",       TokenType.END);
        reservedWords.put("FILE",      TokenType.FILE);
        reservedWords.put("FOR",       TokenType.FOR);
        reservedWords.put("FUNCTION",  TokenType.FUNCTION);
        reservedWords.put("GOTO",      TokenType.GOTO);
        reservedWords.put("IF",        TokenType.IF);
        reservedWords.put("IN",        TokenType.IN);
        reservedWords.put("LABEL",     TokenType.LABEL);
        reservedWords.put("MOD",       TokenType.MOD);
        reservedWords.put("NIL",       TokenType.NIL);
        reservedWords.put("NOT",       TokenType.NOT);
        reservedWords.put("OF",        TokenType.OF);
        reservedWords.put("OR",        TokenType.OR);
        reservedWords.put("PACKED",    TokenType.PACKED);
        reservedWords.put("PROCEDURE", TokenType.PROCEDURE);
        reservedWords.put("PROGRAM",   TokenType.PROGRAM);
        reservedWords.put("RECORD",    TokenType.RECORD);
        reservedWords.put("REPEAT",    TokenType.REPEAT);
        reservedWords.put("SET",       TokenType.SET);
        reservedWords.put("THEN",      TokenType.THEN);
        reservedWords.put("TO",        TokenType.TO);
        reservedWords.put("TYPE",      TokenType.TYPE);
        reservedWords.put("UNTIL",     TokenType.UNTIL);
        reservedWords.put("VAR",       TokenType.VAR);
        reservedWords.put("WHILE",     TokenType.WHILE);
        reservedWords.put("WITH",      TokenType.WITH);
    }
    
    public TokenType type;       // what type of token
    public int lineNumber = 0;   // source line number of the token
    public String text = "";     // text of the token
    public Object value = null;  // the value (if any) of the token
    
    private static Boolean sawDot = false;
    
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
             Character.isLetterOrDigit(ch) || (ch == '_');
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
        char prevChar = firstChar;
        int pointCount = 0;
        
        sawDot = false;
        
        // Loop to get the rest of the characters of the number token.
        // Append digits to the token.
        for (char ch = source.nextChar();
             Character.isDigit(ch) || (ch == '.');
             ch = source.nextChar())
        {
            if (ch == '.') 
            {
                if (prevChar == '.') 
                {
                    pointCount--;
                    token.text = token.text.substring(0, token.text.length() - 1);
                    sawDot = true;
                    break;
                }
                else pointCount++;
            }
            
            token.text += ch;
            prevChar = ch;
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
        
        else
        {
            token.type = TokenType.ERROR;
            tokenError(token, "Invalid number");
        }
        
        return token;
    }
    
    /**
     * Construct a character or string token and set its value.
     * @param firstChar the first character of the token.
     * @param source the input source.
     * @return the string token.
     */
    public static Token characterOrString(char firstChar, Source source)
    {
        Token token = new Token(firstChar);  // the leading '
        token.lineNumber = source.lineNumber();
        int length = 0;                      // string length

        // Loop to append the rest of the characters of the string,
        // up to but not including the closing quote.
        boolean done = false;
        char ch = source.nextChar();
        do
        {
            // Append characters to the string until ' or EOF.
            while ((ch != '\'') && (ch != Source.EOF))
            {
                token.text += ch;
                length++;
                
                // Consume the character.
                ch = source.nextChar();
            }
            
            // End of file. An unclosed string.
            if (ch == Source.EOF)
            {
                tokenError(token, "String not closed");
                done = true;
            }
            
            // Got a ' so it can be the closing ', or a ''
            else
            {
                ch = source.nextChar();  // consume the '
                
                // That was the closing '. Close the string.
                if (ch != '\'') 
                {
                    token.text += '\'';
                    done = true;
                }
                
                // It's '' so append ' to the string.
                else
                {
                    token.text += '\'';
                    length++;
                    
                    // Consume second '
                    ch = source.nextChar();  
                }
            }
        } while (!done);
        
        // It's a character token if the string length is 1.
        // Otherwise, it's a string token.
        token.type = length == 1 ? TokenType.CHARACTER : TokenType.STRING;
        
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
            case ',' : token.type = TokenType.COMMA;      break;
            case ';' : token.type = TokenType.SEMICOLON;  break;
            case '+' : token.type = TokenType.PLUS;       break;
            case '-' : token.type = TokenType.MINUS;      break;
            case '*' : token.type = TokenType.STAR;       break;
            case '/' : token.type = TokenType.SLASH;      break;
            case '=' : token.type = TokenType.EQUALS;     break;
            case '(' : token.type = TokenType.LPAREN;     break;
            case ')' : token.type = TokenType.RPAREN;     break;
            case '[' : token.type = TokenType.LBRACKET;   break;
            case ']' : token.type = TokenType.RBRACKET;   break;
            case '^' : token.type = TokenType.CARAT;      break;

//            case '-' : {
//                char nextChar = source.nextChar();
//                if (nextChar isnt a number) {
//                    token.type = TokenType.MINUS;
//                    break;
//                }
//                else {
//                    parse the number
//                }
//            }

            case ':' : 
            {
                // Consume :
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
                // Consume <
                char nextChar = source.nextChar();  
                
                // Is it the <= symbol?
                if (nextChar == '=') 
                {
                    token.text += '=';
                    token.type = TokenType.LESS_EQUALS;
                }
                
                // Is it the <> symbol?
                else if (nextChar == '>') 
                {
                    token.text += '>';
                    token.type = TokenType.NOT_EQUALS;
                }

                // No, it's just the < symbol.
                else
                {
                    token.type = TokenType.LESS_THAN;
                    return token;  // already consumed <
                }

                break;
            }
            
            case '>' : 
            {
                // Consume >
                char nextChar = source.nextChar();  
                
                // Is it the >= symbol?
                if (nextChar == '=') 
                {
                    token.text += '=';
                    token.type = TokenType.GREATER_EQUALS;
                }
                
                // No, it's just the > symbol.
                else
                {
                    token.type = TokenType.GREATER_THAN;
                    return token;  // already consumed >
                }

                break;
            }
            
            case '.' : 
            {
                // Was the previously consumed character a point?
                if (sawDot)
                {
                    token.text += '.';
                    token.type = TokenType.DOT_DOT;
                }
                else
                {
                    // Consume .
                    char nextChar = source.nextChar();  
                    
                    // Is the next char a point, or have we
                    // previously consumed a point? If so,
                    // it's the .. symbol.
                    if (sawDot || (nextChar == '.'))
                    {
                        token.text += '.';
                        token.type = TokenType.DOT_DOT;
                    }
                    
                    // No, it's just the . symbol.
                    else
                    {
                        token.type = TokenType.PERIOD;
                        return token;  // already consumed .
                    }
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
        
        // Consume the special symbol
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
