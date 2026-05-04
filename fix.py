with open("jolt-example/src/test/java/dev/akre/covenant/jolt/JoltCovenantCheckerTest.java", "r") as f:
    content = f.read()

content = content.replace('Object<matches "^.*$": Object<Range: Number, Value: ?Number, Id: String, ...>, ...>', 'Object<Object<Range: Number, Value: ?Number, Id: String, ...>, ...>')

with open("jolt-example/src/test/java/dev/akre/covenant/jolt/JoltCovenantCheckerTest.java", "w") as f:
    f.write(content)

with open("jolt-example/src/main/java/dev/akre/covenant/jolt/JoltCovenantChecker.java", "r") as f:
    content = f.read()

content = content.replace('replace("*", "matches \\"^.*$\\"");', 'replace("*", "");')

with open("jolt-example/src/main/java/dev/akre/covenant/jolt/JoltCovenantChecker.java", "w") as f:
    f.write(content)
