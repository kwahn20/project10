/*
 * Authors: Haoyu Song and Dale Skrien
 * Date: Spring and Summer, 2018
 *
 * In the grammar below, the variables are enclosed in angle brackets.
 * The notation "::=" is used instead of "-->" to separate a variable from its rules.
 * The special character "|" is used to separate the rules for each variable.
 * All other symbols in the rules are terminals.
 * EMPTY indicates a rule with an empty right hand side.
 * All other terminal symbols that are in all caps correspond to keywords.
 */
package proj10AhnDeGrawHangSlager.bantam.parser;

import org.reactfx.value.Var;
import proj10AhnDeGrawHangSlager.bantam.ast.*;
import proj10AhnDeGrawHangSlager.bantam.lexer.Scanner;
import proj10AhnDeGrawHangSlager.bantam.lexer.Token;
import proj10AhnDeGrawHangSlager.bantam.util.CompilationException;
import proj10AhnDeGrawHangSlager.bantam.util.Error;
import proj10AhnDeGrawHangSlager.bantam.util.ErrorHandler;
import proj10AhnDeGrawHangSlager.bantam.visitor.Visitor;

import java.util.Set;

import static proj10AhnDeGrawHangSlager.bantam.lexer.Token.Kind;
import static proj10AhnDeGrawHangSlager.bantam.lexer.Token.Kind.*;

/**
 * This class constructs an AST from a legal Bantam Java program.  If the
 * program is illegal, then one or more error messages are displayed.
 */
public class Parser
{
    // instance variables
    private Scanner scanner;
    private Token currentToken; // the lookahead token
    private ErrorHandler errorHandler;

    // constructor
    public Parser(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }


    /**
     * parse the given file and return the root node of the AST
     * @param filename The name of the Bantam Java file to be parsed
     * @return The Program node forming the root of the AST generated by the parser
     */
    public Program parse(String filename) {
        this.scanner = new Scanner(filename, this.errorHandler);
        this.currentToken = scanner.scan();
        return parseProgram();
    }


    /*
     * <Program> ::= <Class> | <Class> <Program>
     */
    private Program parseProgram() {

        int position = currentToken.position;
        ClassList classList = new ClassList(position);
        while (currentToken.kind != EOF) {
            Class_ aClass = parseClass();
            classList.addElement(aClass);
        }

        return new Program(position, classList);
    }


    /*
	 * <Class> ::= CLASS <Identifier> <ExtendsClause> { <MemberList> }
     * <ExtendsClause> ::= EXTENDS <Identifier> | EMPTY
     * <MemberList> ::= EMPTY | <Member> <MemberList>
     */
    private Class_ parseClass() {

        if (this.currentToken.kind != CLASS) {
            this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID CLASS DECLARATION");
        }
        this.currentToken = scanner.scan();
        String left = parseIdentifier();

        this.currentToken = scanner.scan();
        String parent = null;
        if (this.currentToken.kind == EXTENDS) {
            this.currentToken = scanner.scan();
            parent = parseIdentifier();
        }
        if (this.currentToken.kind == LCURLY) {
            this.currentToken = scanner.scan();

            MemberList memberList = new MemberList(this.currentToken.position);
            Member member = null;


            while (currentToken.kind != RCURLY) {
                member = parseMember();
                memberList.addElement(member);
                this.currentToken = scanner.scan();
            }
            return new Class_(this.currentToken.position, left.concat(".btm"), left, parent, memberList);
        }
        else {
            this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID CLASS DECLARATION");
            return null;
        }
    }


    /* Fields and Methods
     * <Member> ::= <Field> | <Method>
     * <Method> ::= <Type> <Identifier> ( <Parameters> ) <Block>
     * <Field> ::= <Type> <Identifier> <InitialValue> ;
     * <InitialValue> ::= EMPTY | = <Expression>
     */
     private Member parseMember() {
         String type = parseType();
         String name = parseIdentifier();
         this.currentToken = scanner.scan();

         if (this.currentToken.kind == LPAREN) {

             FormalList params = parseParameters();

             if (this.currentToken.kind == RPAREN) {
                 this.currentToken = scanner.scan();
                 BlockStmt block = (BlockStmt)parseBlock();
                 return new Method(this.currentToken.position, type, name, params, block.getStmtList());
             }
               this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID MEMBER DECLARATION");

         }
         else {
             Expr init = null;
             if (this.currentToken.kind == ASSIGN) {
                init = parseExpression();
             }

             this.currentToken = scanner.scan();
             return new Field(this.currentToken.position, type, name, init);

         }
         return null;
     }


    //-----------------------------------

    /* Statements
     *  <Stmt> ::= <WhileStmt> | <ReturnStmt> | <BreakStmt> | <DeclStmt>
     *              | <ExpressionStmt> | <ForStmt> | <BlockStmt> | <IfStmt>
     */
     private Stmt parseStatement() {
            Stmt stmt;

            switch (currentToken.kind) {
                case IF:
                    stmt = parseIf();
                case LCURLY:
                    stmt = parseBlock();
                    break;
                case VAR:
                    stmt = parseDeclStmt();
                    break;
                case RETURN:
                    stmt = parseReturn();
                    break;
                case FOR:
                    stmt = parseFor();
                    break;
                case WHILE:
                    stmt = parseWhile();
                    break;
                case BREAK:
                    stmt = parseBreak();
                    break;
                default:
                    stmt = parseExpressionStmt();
            }

            if(stmt !=null){
                return stmt;
            }
            else{
                this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID STATEMENT");
                return null;
            }

    }


    /*
     * <WhileStmt> ::= WHILE ( <Expression> ) <Stmt>
     */
    private Stmt parseWhile() {
        Expr expr = parseExpression();
        this.currentToken = scanner.scan();
        Stmt stmt = parseStatement();
        return new WhileStmt(this.currentToken.position,expr,stmt);
    }


    /*
     * <ReturnStmt> ::= RETURN <Expression> ; | RETURN ;
     */
	private Stmt parseReturn() {

        this.currentToken = scanner.scan();
        Expr right = null;
        while (this.currentToken.kind != SEMICOLON) {
            right = parseExpression();
            this.currentToken = scanner.scan();
        }
	    return new ReturnStmt(this.currentToken.position, right);
    }


    /*
	 * BreakStmt> ::= BREAK ;
     */
	private Stmt parseBreak() {
	    this.currentToken = scanner.scan();
	    if (this.currentToken.kind != SEMICOLON) {
	        this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID BREAK STATEMENT");
        }
	    return new BreakStmt(this.currentToken.position);
    }


    /*
	 * <ExpressionStmt> ::= <Expression> ;
     */
	private ExprStmt parseExpressionStmt() {
        this.currentToken = scanner.scan();
        Expr expr = parseExpression();
        return new ExprStmt(this.currentToken.position, expr);
    }

    /*
	 * <DeclStmt> ::= VAR <Identifier> = <Expression> ;
     * every local variable must be initialized
     */
	private Stmt parseDeclStmt() {
        String name = null;
        Expr init = null;
	    if (this.currentToken.kind == VAR){
	        while (this.currentToken.kind != COMPARE){
                name = parseIdentifier();
                this.currentToken = scanner.scan();
            }
	        while (this.currentToken.kind != SEMICOLON) {
                init = parseExpression();
                this.currentToken = scanner.scan();
            }
        }
        return new DeclStmt(this.currentToken.position,name,init);
    }


    /*
	 * <ForStmt> ::= FOR ( <Start> ; <Terminate> ; <Increment> ) <STMT>
     * <Start>     ::= EMPTY | <Expression>
     * <Terminate> ::= EMPTY | <Expression>
     * <Increment> ::= EMPTY | <Expression>
     */
	private Stmt parseFor() {
	    Stmt stmt = null;
        Expr initExpr = null;
        Expr predExpr = null;
        Expr updateExpr = null;

        while (!this.currentToken.getSpelling().equals(";")){
            this.currentToken = scanner.scan();
            initExpr = parseExpression();
        }

        while (!this.currentToken.getSpelling().equals(";")){
            this.currentToken = scanner.scan();
            predExpr = parseExpression();
        }

        while (this.currentToken.kind != RPAREN){
            this.currentToken = scanner.scan();
            updateExpr = parseExpression();
        }

        while(this.currentToken.kind != RBRACKET){
            this.currentToken = scanner.scan();
            stmt = parseStatement();
        }

        return new ForStmt(this.currentToken.position, initExpr, predExpr, updateExpr, stmt);
    }


    /*
	 * <BlockStmt> ::= { <Body> }
     * <Body> ::= EMPTY | <Stmt> <Body>
     */
	private Stmt parseBlock() {
        StmtList listOfNodes = new StmtList(this.currentToken.position);
        while(this.currentToken.kind != RCURLY){
            currentToken = scanner.scan();
            listOfNodes.addElement(parseStatement());
        }
        return new BlockStmt(this.currentToken.position, listOfNodes);
    }


    /*
	 * <IfStmt> ::= IF ( <Expr> ) <Stmt> | IF ( <Expr> ) <Stmt> ELSE <Stmt>
     */
	private Stmt parseIf() {

	    Expr left = parseExpression();

	    this.currentToken = scanner.scan();
	    Stmt right = parseStatement();

        this.currentToken = scanner.scan();
        Stmt thenStmt = null;

        if (this.currentToken.kind == ELSE) {
            this.currentToken = scanner.scan();
	        thenStmt = parseStatement();
        }
        return new IfStmt(this.currentToken.position, left, right, thenStmt);
    }


    //-----------------------------------------
    // Expressions
    //Here we introduce the precedence to operations

    /*
	 * <Expression> ::= <LogicalOrExpr> <OptionalAssignment>
     * <OptionalAssignment> ::= EMPTY | = <Expression>f
     */
	private Expr parseExpression() {
        Expr right = null;
        String name = "";
	    Expr left = parseOrExpr();
        name = this.currentToken.spelling;
        while (this.currentToken.kind == ASSIGN){
	        this.currentToken = scanner.scan();
	        right = parseExpression();
        }

        if(right!=null){
            return new AssignExpr(this.currentToken.position,left.getExprType(), name, right  );
        }
        return left;
    } //-----------------------------------------------------------------


    /*
	 * <LogicalOR> ::= <logicalAND> <LogicalORRest>
     * <LogicalORRest> ::= EMPTY |  || <LogicalAND> <LogicalORRest>
     */
	private Expr parseOrExpr() {
        int position = currentToken.position;

        Expr left = parseAndExpr();
        while (this.currentToken.kind == BINARYLOGIC) {
            this.currentToken = scanner.scan();
            Expr right = parseAndExpr();
            left = new BinaryLogicOrExpr(position, left, right);
        }

        return left;
	}

    /*
	 * <LogicalAND> ::= <ComparisonExpr> <LogicalANDRest>
     * <LogicalANDRest> ::= EMPTY |  && <ComparisonExpr> <LogicalANDRest>
     */
	private Expr parseAndExpr() {
        int position = currentToken.position;

        Expr left = parseEqualityExpr();
        while (this.currentToken.kind == BINARYLOGIC) {
            this.currentToken = scanner.scan();
            Expr right = parseEqualityExpr();
            left = new BinaryLogicAndExpr(position, left, right);
        }

        return left;

    }

    /*
	 * <ComparisonExpr> ::= <RelationalExpr> <equalOrNotEqual> <RelationalExpr> |
     *                     <RelationalExpr>
     * <equalOrNotEqual> ::=  == | !=
     */
	private Expr parseEqualityExpr() {
        int position = this.currentToken.position;
        Expr left = parseRelationalExpr();
        if (this.currentToken.kind == COMPARE) {
            Expr right;
            if(this.currentToken.spelling.equals("!=")){
                this.currentToken = scanner.scan();
                right = parseRelationalExpr();
                left = new BinaryCompNeExpr(position, left, right);
            }
            else if(this.currentToken.spelling.equals("==")){
                this.currentToken = scanner.scan();
                right = parseRelationalExpr();
                left = new BinaryCompEqExpr(position, left, right);
            }
        }
        return left;
    }

    /*
	 * <RelationalExpr> ::=<AddExpr> | <AddExpr> <ComparisonOp> <AddExpr>
     * <ComparisonOp> ::=  < | > | <= | >= | INSTANCEOF
     */
	private Expr parseRelationalExpr() {

        Expr left = parseAddExpr();
        if (this.currentToken.kind == COMPARE || this.currentToken.kind == INSTANCEOF) {
            switch(currentToken.getSpelling()) {
                case "<":
                    this.currentToken = scanner.scan();
                    return new BinaryCompLtExpr(this.currentToken.position, left, parseAddExpr());
                case ">":
                    this.currentToken = scanner.scan();
                    return new BinaryCompGtExpr(this.currentToken.position, left, parseAddExpr());
                case "<=":
                    this.currentToken = scanner.scan();
                    return new BinaryCompLeqExpr(this.currentToken.position, left, parseAddExpr());
                case ">=":
                    this.currentToken = scanner.scan();
                    return new BinaryCompGeqExpr(this.currentToken.position, left, parseAddExpr());
                case "instanceof":
                    this.currentToken = scanner.scan();
                    return new InstanceofExpr(this.currentToken.position, left, parseType());
            }
        }

        return left;
    }

    /*
	 * <AddExpr>::＝ <MultExpr> <MoreMultExpr>
     * <MoreMultExpr> ::= EMPTY | + <MultExpr> <MoreMultExpr> | - <MultExpr> <MoreMultExpr>
     */
	private Expr parseAddExpr() {

        Expr left = parseMultExpr();
        Expr right = null;
        while (this.currentToken.kind == PLUSMINUS) {
            this.currentToken = scanner.scan();
            right = parseAddExpr();
        }
        return new BinaryArithPlusExpr(this.currentToken.position, left, right);
    }

    /*
	 * <MultiExpr> ::= <NewCastOrUnary> <MoreNCU>
     * <MoreNCU> ::= * <NewCastOrUnary> <MoreNCU> |
     *               / <NewCastOrUnary> <MoreNCU> |
     *               % <NewCastOrUnary> <MoreNCU> |
     *               EMPTY
     */
	private Expr parseMultExpr() {

	    Expr left = parseNewCastOrUnary();
	    while (this.currentToken.kind == MULDIV) {
	        switch(this.currentToken.getSpelling()) {
                case "*":
                    this.currentToken = scanner.scan();
                    return new BinaryArithTimesExpr(this.currentToken.position, left, parseMultExpr());
                case "/":
                    this.currentToken = scanner.scan();
                    return new BinaryArithDivideExpr(this.currentToken.position, left, parseMultExpr());
                case "%":
                    this.currentToken = scanner.scan();
                    return new BinaryArithDivideExpr(this.currentToken.position, left, parseMultExpr());
//                case "NEW":
//
                default:
                    this.errorHandler.register(Error.Kind.LEX_ERROR,
                            "MULDIV TOKEN IS : " + this.currentToken.getSpelling());
                    break;
            }
//            this.currentToken = scanner.scan();
        }
        return null;
    }

    /*
	 * <NewCastOrUnary> ::= < NewExpression> | <CastExpression> | <UnaryPrefix>
     */
	private Expr parseNewCastOrUnary() {
	    if (this.currentToken.kind == CAST) return parseCast();
	    else if (this.currentToken.kind == NEW ) return parseNew();
	    else if(this.currentToken.kind == UNARYDECR || this.currentToken.kind == UNARYINCR
                || this.currentToken.kind == UNARYNOT || this.currentToken.spelling.equals("-") ){
	        return parseUnaryPrefix();
        }
        else{
            this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID CAST OR UNARY TOKEN");
            return null;

        }
    }


    /*
	 * <NewExpression> ::= NEW <Identifier> ( ) | NEW <Identifier> [ <Expression> ]
     */
	private Expr parseNew() {

        if (this.currentToken.kind == NEW){
            this.currentToken = scanner.scan();
            String identifier = parseIdentifier();

            this.currentToken = scanner.scan();
            if (this.currentToken.kind == LPAREN) {

                this.currentToken = scanner.scan();
                if (this.currentToken.kind == RPAREN) {
                    return new NewExpr(this.currentToken.position, identifier);
                }
                else {
                    this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID NEW EXPRESSION");
                    return null;
                }
            }
            else if (this.currentToken.kind == LBRACKET) {
                this.currentToken = scanner.scan();
                Expr expression = parseExpression();
                this.currentToken = scanner.scan();
                if (this.currentToken.kind == RBRACKET) {
                    return new NewArrayExpr(this.currentToken.position, identifier, expression);
                }
                else {
                    this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID NEW ARRAY EXPRESSION");
                    return null;
                }
            }
        }
        this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID NEW EXPRESSION");
        return null;
    }


    /*
	 * <CastExpression> ::= CAST ( <Type> , <Expression> )
     */
	private Expr parseCast() {
        if (this.currentToken.kind == LPAREN) {
            String left = parseType();
            this.currentToken = scanner.scan();
            if (this.currentToken.kind == COMMA) {
                this.currentToken = scanner.scan();
                Expr right = parseExpression();
                this.currentToken = scanner.scan();
                if (this.currentToken.kind == RPAREN) {
                    return new CastExpr(this.currentToken.position, left, right);
                }
            }
        }
        this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID CAST EXPRESSION");
        return null;
    }


    /*
	 * <UnaryPrefix> ::= <PrefixOp> <UnaryPrefix> | <UnaryPostfix>
     * <PrefixOp> ::= - | ! | ++ | --
     */
	private Expr parseUnaryPrefix() {
	    boolean isUnaryPref = this.currentToken.kind == UNARYDECR || this.currentToken.kind == UNARYINCR
                || this.currentToken.kind == UNARYNOT || this.currentToken.spelling.equals("-");

	    while(isUnaryPref){
            switch(currentToken.kind) {
                case UNARYINCR:
                    this.currentToken = scanner.scan();
                    return new UnaryIncrExpr(this.currentToken.position, parseUnaryPrefix(), false);
                case UNARYDECR:
                    this.currentToken = scanner.scan();
                    return new UnaryDecrExpr(this.currentToken.position, parseUnaryPrefix(), false);
                case UNARYNOT:
                    this.currentToken = scanner.scan();
                    return new UnaryNotExpr(this.currentToken.position, parseUnaryPrefix());
                case PLUSMINUS:
                    this.currentToken = scanner.scan();
                    return new UnaryNegExpr(this.currentToken.position, parseUnaryPrefix());
            }
        }
        return parseUnaryPostfix();
    }


    /*
	 * <UnaryPostfix> ::= <Primary> <PostfixOp>
     * <PostfixOp> ::= ++ | -- | EMPTY
     */
	private Expr parseUnaryPostfix() {
	    Expr left = parsePrimary();
	    this.currentToken = scanner.scan();

	    switch(currentToken.kind) {
            case UNARYINCR: return new UnaryIncrExpr(this.currentToken.position, left, true);
            case UNARYDECR: return new UnaryDecrExpr(this.currentToken.position, left, true);
        }
        return left;
    }


    /*
	 * <Primary> ::= ( <Expression> ) | <IntegerConst> | <BooleanConst> |
     *                               <StringConst> | <VarExpr> | <DispatchExpr>
     * <VarExpr> ::= <VarExprPrefix> <Identifier> <VarExprSuffix>
     * <VarExprPrefix> ::= <Identifier> . | EMPTY
     * <VarExprSuffix> ::= [ <Expr> ] | EMPTY
     * <DispatchExpr> ::= <DispatchExprPrefix> <Identifier> ( <Arguments> )
     * <DispatchExprPrefix> ::= <Primary> . | EMPTY
     */
	private Expr parsePrimary() {
	    if (this.currentToken.kind == LPAREN) {
	        return parseExpression();
        }
        switch(this.currentToken.kind) {
            case INTCONST: return parseIntConst();
            case BOOLEAN: return parseBoolean();
            case STRCONST: return parseStringConst();
            case VAR: return parseVarExpr();
            default: return parseDispatchExpr();
        }
    }

    /*
     * <VarExpr> ::= <VarExprPrefix> <Identifier> <VarExprSuffix>
     * <VarExprPrefix> ::=  <Identifier> . | EMPTY
     * <VarExprSuffix> ::= [ <Expr> ] | EMPTY
     */
    private Expr parseVarExpr() {
        String id;
        Expr varExprSuffix;

        if(this.currentToken.spelling.equals("super") || this.currentToken.spelling.equals("this")){
            id = currentToken.spelling;
        }
        else{
            id = parseIdentifier();
        }


        this.currentToken = scanner.scan();
        if(this.currentToken.kind == LBRACKET){
            this.currentToken = scanner.scan();
            varExprSuffix = parseExpression();
            return new ArrayExpr(this.currentToken.position, null, id, varExprSuffix);

        }
        else if(this.currentToken.kind == IDENTIFIER){
            String identifier = currentToken.spelling;
            VarExpr idVar = new VarExpr(this.currentToken.position, null, identifier);
            return new VarExpr(this.currentToken.position, idVar, id);
        }
        else{
            this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID VAR EXPRESSION");
            return null;
        }
    }

    /*
     * <DispatchExpr> ::= <DispatchExprPrefix> <Identifier> ( <Arguments> )
     * <DispatchExprPrefix> ::= <Primary> . | EMPTY
     */
    private Expr parseDispatchExpr() {
        Expr expr = null;
        ExprList args = null;
        String id = null;

        if(this.currentToken.kind == IDENTIFIER){
            id = parseIdentifier();
        }
        else{
            expr = parsePrimary();
            this.currentToken = scanner.scan();
        }

        this.currentToken = scanner.scan();
        if(this.currentToken.kind == LPAREN){
            this.currentToken = scanner.scan();
            args= parseArguments();
        }

        return new DispatchExpr(this.currentToken.position,expr,id,args);
    }

    /*
	 * <Arguments> ::= EMPTY | <Expression> <MoreArgs>
     * <MoreArgs>  ::= EMPTY | , <Expression> <MoreArgs>
     */
	private ExprList parseArguments() {
	    ExprList eList = new ExprList(this.currentToken.position);
	    Expr expr = parseExpression();
	    eList.addElement(expr);
        this.currentToken = scanner.scan();
	    while(this.currentToken.kind == COMMA){
	        this.currentToken = scanner.scan();
	        eList.addElement(parseExpression());
        }
	    return new ExprList(this.currentToken.position);
    }


    /*
	 * <Parameters>  ::= EMPTY | <Formal> <MoreFormals>
     * <MoreFormals> ::= EMPTY | , <Formal> <MoreFormals
     */
	private FormalList parseParameters() {

	    FormalList formalList = new FormalList(this.currentToken.position);
	    Formal left = parseFormal();
	    formalList.addElement(left);
	    while (this.currentToken.kind == COMMA) {
            formalList.addElement(parseFormal());
            this.currentToken = scanner.scan();
        }
        return formalList;
    }


    /*
	 * <Formal> ::= <Type> <Identifier>
     */
	private Formal parseFormal() {
	    String type = parseType();
        String identifier = parseIdentifier();
        return new Formal(this.currentToken.position, type, identifier);
    }


    /*
	 * <Type> ::= <Identifier> <Brackets>
     * <Brackets> ::= EMPTY | [ ]
     */
	private String parseType() {
        String s = parseIdentifier();

        this.currentToken = scanner.scan();
        if (this.currentToken.kind == LBRACKET) {
            this.currentToken = scanner.scan();
            if (this.currentToken.kind == RBRACKET) {
                s = s.concat("[]");
                return s;
            }
            else{
                this.errorHandler.register(Error.Kind.PARSE_ERROR, "INVALID TYPE EXPRESSION");
                return null;
            }
        }
        return s;
    }


    //----------------------------------------
    //Terminals

	private String parseOperator() {

        Kind kind = this.currentToken.kind;
        if (kind == BINARYLOGIC || kind == PLUSMINUS || kind ==  MULDIV || kind == COMPARE
            || kind == UNARYDECR || kind == UNARYINCR || kind == ASSIGN || kind == UNARYNOT) {

            return this.currentToken.getSpelling();
        }
        return null;
    }


    private String parseIdentifier() {
        if (this.currentToken.kind == IDENTIFIER) {
            return this.currentToken.getSpelling();
        }
        return null;
    }


    private ConstStringExpr parseStringConst() {
	    if (this.currentToken.kind == STRCONST) {
	        return new ConstStringExpr(this.currentToken.position, this.currentToken.getSpelling());
        }
        return null;
    }


    private ConstIntExpr parseIntConst() {
	    if (this.currentToken.kind == INTCONST) {
	        return new ConstIntExpr(this.currentToken.position, this.currentToken.getSpelling());
        }
        return null;
    }


    private ConstBooleanExpr parseBoolean() {
	    if (this.currentToken.kind == BOOLEAN) {
	        return new ConstBooleanExpr(this.currentToken.position, this.currentToken.getSpelling());
        }
        return null;
    }

    public static void main(String[] args){
        if(args.length > 0){
            for(int i = 0; i< args.length; i ++){
                ErrorHandler errorHandler = new ErrorHandler();
                Parser parser = new Parser(errorHandler);
                parser.parse(args[i]);


                if(errorHandler.getErrorList().size() > 0){
                    System.out.println("Scanning of " + args[i] + " was not successful. "+
                            errorHandler.getErrorList().size() +" errors were found.\n\n");
                }
                else{

                    System.out.println("Scanning of " + args[i] + " was successful. " +
                            "No errors were found.\n\n");
                }


            }
        }

    }

}




