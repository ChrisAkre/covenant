with open("core/src/main/java/dev/akre/covenant/types/NormalizerUtils.java", "r") as f:
    code = f.read()

bad1 = """if (self instanceof NegationType(TypeDef inner) && other instanceof NegationType(TypeDef otherInner)) {"""
good1 = """if (self instanceof NegationType && other instanceof NegationType) {
                TypeDef inner = ((NegationType) self).def();
                TypeDef otherInner = ((NegationType) other).def();"""

bad2 = """} else if (self instanceof NegationType(TypeDef inner)) {"""
good2 = """} else if (self instanceof NegationType) {
                TypeDef inner = ((NegationType) self).def();"""

bad3 = """} else if (other instanceof NegationType(TypeDef otherInner)) {"""
good3 = """} else if (other instanceof NegationType) {
                TypeDef otherInner = ((NegationType) other).def();"""

code = code.replace(bad1, good1).replace(bad2, good2).replace(bad3, good3)

with open("core/src/main/java/dev/akre/covenant/types/NormalizerUtils.java", "w") as f:
    f.write(code)
