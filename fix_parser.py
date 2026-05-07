with open("core/src/main/java/dev/akre/covenant/types/TypeParser.java", "r") as f:
    code = f.read()

bad = """if (result instanceof Parser.Success<TypeExpr>(TypeExpr value, Parser.InputState remaining)) {"""
good = """if (result instanceof Parser.Success<TypeExpr> success) {
                TypeExpr value = success.value();
                Parser.InputState remaining = success.remaining();"""

code = code.replace(bad, good)

with open("core/src/main/java/dev/akre/covenant/types/TypeParser.java", "w") as f:
    f.write(code)
