import pandas as pd

with open("buku_pedoman_istts_clean.txt", "r", encoding="utf-8") as file:
	lines = file.readlines()

splits = ''.join(lines).split("\n\n")

result = []

for i in range(len(splits)):
	split = splits[i]
	firstlineend = split.find("\n")
	firstline = split[:firstlineend].strip()
	if firstline.replace(" ","").isalnum() and firstline.isupper():
		result.append(split)
	else:
		result[-1] = result[-1] + "\n\n" + split
		

df = pd.DataFrame({
	"text": result
})

df.to_csv("buku_pedoman_istts_clean.csv")