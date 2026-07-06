# Script para escribir cookies con formato de tabulador estricto
import os

cookies_lines = [
    "# Netscape HTTP Cookie File",
    "# https://curl.haxx.se/rfc/cookie_spec.html",
    "# This is a generated file! Do not edit.",
    "",
    ".youtube.com\tTRUE\t/\tTRUE\t1815674393\tLOGIN_INFO\tAFmmF2swRQIhALGim1MtIypLx0GO-qKpEbLtCBsEoU3jO6E5hf8zzYBGAiBuNGbxGRrlXQ_yqJoxjn0bd30QVu-Zkfe2xwMxxeisMQ:QUQ3MjNmeXNLMFE0bzNobUtQdFBtdXk3dllsQjJGQk0wcWNJZWoxenRURUthZ3lDM2VOMjd5M05MSGpNQWxwLXZPY2pTWF9RRXBnZDR6cWx6X0M2SlFyM3A1SDNLdnhtUFpvZnMzcFJ0aWk2WnNTVlgydTBrbWZ5U1lFUzdQQkhzYVFjZFZuR29PNGhtYWEtT1ZTMHFhblhiVlBadVlGa293",
    ".youtube.com\tTRUE\t/\tTRUE\t1817912747\tPREF\tf6=40000080&tz=America.Lima&f7=100",
    ".youtube.com\tTRUE\t/\tTRUE\t1815531437\t__Secure-YENID\t16.YTE=P-aALrq9RyHOD4DL32h48GktaFvb-qkpULjdIDfI0FKOQp218EH2eplvxATKc1IFrBBH8Xv0Ybk8KqbjGDDk272FDl4Bgw1kYEnNdJCtlhT6ItyQ45xLiSKXC8JTl14qvT3loC2HBYKSQSNqXTX0jXL7DWRU_1o_WVZOmiS3Fu8qHuqUC5kNsPYdbBcv0bhgHYxgjEQQsWUDwtln_6guUrFKV-unAb8p20HGEdS41bdNcwrshtaFpqEpbSa6q02CHxtxoqoEz9eDM2gtJzuakX--eD0segrU_6OnOrjEdTvWhyIBK6ijwRpZVXwphyApbpQsadFkGrVmIJuhQosHiw",
    ".youtube.com\tTRUE\t/\tFALSE\t1817890793\tSID\tg.a000_wh4pV9-czv1kfBlpC-i16UrIc8sskvom57z8wKTKWf8mtsIApyvncR2B4hxubJePcy9VgACgYKAYASARESFQHGX2MifTT3LAw7m-mEbjLvPpXxHhoVAUF8yKoSjDrbqIckrSnazZpvUXxl0076",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\t__Secure-1PSID\tg.a000_wh4pV9-czv1kfBlpC-i16UrIc8sskvom57z8wKTKWf8mtsIOOEe-e9aI6xGGKNWFexDHAACgYKAW8SARESFQHGX2MiCmF1JZz3SXRTt-RNLs_DbRoVAUF8yKpBTlSVx8I7gQb9-Lg8OTte0076",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\t__Secure-3PSID\tg.a000_wh4pV9-czv1kfBlpC-i16UrIc8sskvom57z8wKTKWf8mtsIGrvfLsTM8SkwoCfRz3pzjgACgYKATkSARESFQHGX2MitGcGrqv0bNU1yk4wHFiewxoVAUF8yKon04TiBOdbuB98eL5ZwqZD0076",
    ".youtube.com\tTRUE\t/\tFALSE\t1817890793\tHSID\tAJZqewsYIyzVkzGb9",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\tSSID\tAhIK9fn_2MwhDnSmY",
    ".youtube.com\tTRUE\t/\tFALSE\t1817890793\tAPISID\tiSmnVwBU_ws0BIXD/AZ28X0ATzju8YspsM",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\tSAPISID\twgh4a0DgT2VrqU5d/AdudCqghGbzAoDqtt",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\t__Secure-1PAPISID\twgh4a0DgT2VrqU5d/AdudCqghGbzAoDqtt",
    ".youtube.com\tTRUE\t/\tTRUE\t1817890793\t__Secure-3PAPISID\twgh4a0DgT2VrqU5d/AdudCqghGbzAoDqtt",
    ".youtube.com\tTRUE\t/\tTRUE\t1814888533\t__Secure-1PSIDTS\tsidts-CjQByojQU5HAUBNCJrm7q8bO7nK-FkfEetwdds83x-PRmMYMqrJROqj30mB0HZMQkzPopezAEAA",
    ".youtube.com\tTRUE\t/\tTRUE\t1814888533\t__Secure-3PSIDTS\tsidts-CjQByojQU5HAUBNCJrm7q8bO7nK-FkfEetwdds83x-PRmMYMqrJROqj30mB0HZMQkzPopezAEAA",
    ".youtube.com\tTRUE\t/\tTRUE\t1783353343\tCONSISTENCY\tAFeheW1K4_wEmldLibpSGPqOp8y4E_wt3LbUspRSyajoR9TSIzr18wYcSiy7lmF_KTd9XAj5fX3FN28A2Wb6UjrucRmOZkKwys9CcDsk6kg0Kydf4kHb4dt9H6r8wf-djwKaKy63qfjR1GWvUABg8qcr",
    ".youtube.com\tTRUE\t/\tFALSE\t1814888750\tSIDCC\tAKEyXzWpYpomKAHq58U4zfVnXO1RKibyPlmsK8fZOqcGVmTQRDLe_EIkQLtlhPgJSqG1DRsG5Q",
    ".youtube.com\tTRUE\t/\tTRUE\t1814888750\t__Secure-1PSIDCC\tAKEyXzWGXxg28_4ip3xd5Qa8qi-_NoVIjezZiMcZSWLZx8w5c0GasWbK0ioKK0gBwlzmaomd4-w",
    ".youtube.com\tTRUE\t/\tTRUE\t1814888750\t__Secure-3PSIDCC\tAKEyXzVjwsVXXKgXQSoJ2KGN233FAhz5B3F-upjMPqZV-I1F28NfnaLX7PqR5oG5eXL2QQX7gA",
    ".youtube.com\tTRUE\t/\tFALSE\t1783352910\tST-1b\tdisableCache=true&itct=CCQQsV4iEwiZncXXsr6VAxUfCx4AHSFID8nKAQTfFYwG&csn=Lsl72eQT2B3KSiGz&session_logininfo=AFmmF2swRQIhALGim1MtIypLx0GO-qKpEbLtCBsEoU3jO6E5hf8zzYBGAiBuNGbxGRrlXQ_yqJoxjn0bd30QVu-Zkfe2xwMxxeisMQ%3AQUQ3MjNmeXNLMFE0bzNobUtQdFBtdXk3dllsQjJGQk0wcWNJZWoxenRURUthZ3lDM2VOMjd5M05MSGpNQWxwLXZPY2pTWF9RRXBnZDR6cWx6X0M2SlFyM3A1SDNLdnhtUFpvZnMzcFJ0aWk2WnNTVlgydTBrbWZ5U1lFUzdQQkhzYVFjZFZuR29PNGhtYWEtT1ZTMHFhblhiVlBadVlGa293&endpoint=%7B%22clickTrackingParams%22%3A%22CCQQsV4iEwiZncXXsr6VAxUfCx4AHSFID8nKAQTfFYwG%22%2C%22commandMetadata%22%3A%7B%22webCommandMetadata%22%3A%7B%22url%22%3A%22%2F%22%2C%22webPageType%22%3A%22WEB_PAGE_TYPE_BROWSE%22%2C%22rootVe%22%3A3854%2C%22apiUrl%22%3A%22%2Fyoutubei%2Fv1%2Fbrowse%22%7D%7D%2C%22browseEndpoint%22%3A%7B%22browseId%22%3A%22FEwhat_to_watch%22%7D%7D",
    ".youtube.com\tTRUE\t/\tFALSE\t1783352910\tST-yve142\tsession_logininfo=AFmmF2swRQIhALGim1MtIypLx0GO-qKpEbLtCBsEoU3jO6E5hf8zzYBGAiBuNGbxGRrlXQ_yqJoxjn0bd30QVu-Zkfe2xwMxxeisMQ%3AQUQ3MjNmeXNLMFE0bzNobUtQdFBtdXk3dllsQjJGQk0wcWNJZWoxenRURUthZ3lDM2VOMjd5M05MSGpNQWxwLXZPY2pTWF9RRXBnZDR6cWx6X0M2SlFyM3A1SDNLdnhtUFpvZnMzcFJ0aWk2WnNTVlgydTBrbWZ5U1lFUzdQQkhzYVFjZFZuR29PNGhtYWEtT1ZTMHFhblhiVlBadVlGa293",
    ".youtube.com\tTRUE\t/\tTRUE\t1798904741\tVISITOR_INFO1_LIVE\tlVr2-xZwvT4",
    ".youtube.com\tTRUE\t/\tTRUE\t1798904741\tVISITOR_PRIVACY_METADATA\tCgJQRRIEGgAgTg%3D%3D",
    ".youtube.com\tTRUE\t/\tTRUE\t1798820709\t__Secure-YNID\t19.YT=dGCCRV9I6pik0eN6iAopng-oqQ197shnzrM6Xv6dgBxYEUytUWXRwvpXcsqaJ7K5LM96Bh1D99zBY510FcMn4h6M9tK7tGaaMR0wp4OEu9-OEjm2qH1VziDaQVPw_iZ_cpXgfA0MD206brgPERxPacrkxw0b8fsY1Fb-UhTFkNEW7bQ7Vf3WXxJn2GgEDu_0osrILgVWzMLSMgLXvUFnFBqSsEJ3L3Ztl0JpCb9XLVUWeIceICyjPOF_LSiuvwb5GbJqgnvaoaMw5g-wqBPd3qhMdyqL5kyvAFVdrd2QSuYGIDr6ECKTbZz1gk0zn_kRA30BO7W6lxKxTc0G_pd5Ww",
    ".youtube.com\tTRUE\t/\tTRUE\t1798820709\t__Secure-ROLLOUT_TOKEN\tCP3Uof3Fy-6nngEQtMqywqD9lAMY1YL60Pm7lQM%3D",
    ".youtube.com\tTRUE\t/\tTRUE\t0\tYSC\tf-5_Z-WgkD8"
]

# Write to cookies.txt in current directory
filepath = os.path.join(os.getcwd(), "cookies.txt")
with open(filepath, "w", encoding="utf-8") as f:
    f.write("\n".join(cookies_lines) + "\n")

print(f"Cookies written successfully to: {filepath} with strict tabs!")
