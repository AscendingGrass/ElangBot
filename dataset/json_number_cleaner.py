with open("gemini_generated_chatml_numbered.json", "r") as file:
	lines = file.readlines()

result = []

for i in range(len(lines)):
	if lines[i].find('"number"') == -1:
		result.append(lines[i])

with open("gemini_generated_chatml.json", "w") as file:
	lines = file.writelines(result)