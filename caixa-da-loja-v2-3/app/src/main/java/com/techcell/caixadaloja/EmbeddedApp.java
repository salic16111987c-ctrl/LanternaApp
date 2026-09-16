package com.techcell.caixadaloja;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class EmbeddedApp {
    private static final String DATA =
            "H4sIANTtqWoC/9V9W3PjSJbee/8KNHumAFggRFKqGymQy5KoLs2qpLKo6u5ZWdEBAkkRXbiwAVCimmLETjjs3Rc/eHZ9i3DEeuwI" +
            "b9gR87RvfnT9k/kF+xN8Tl6ABAhSrKqe8e7OdhHIe548l++cTKQOvnQjJ72fEmWSBn73iwP8UXw7vLFq07T+6qKGacR24Scgqa04" +
            "EztOSGrVZum4/qImkkM7IFbt1iN30yhOa4oThSkJodid56YTyyW3nkPq9MXwQi/1bL+eOLZPrKYhatXHXmo50S2JS82mExKQuhP5" +
            "USy1/FVj1Hy218CyqZf6pHtoe3NbcW3lNPrBVlrms4NdlvHFQZLe4287jqJ0Ua+PbtpfjffGT8cvO/W6Y8cuvI7H8OyF79tfNRvN" +
            "F60X8BbMUgJZz549b7x4Cu++F5L2V2SfPCcOvIb27X2bjwJeb2JCQqj9YvTyOdaOse5ov7XXxLdkYrvRXbuhNBvTudJ6Af/ENyNb" +
            "az4zWvvGfsMwGy/05b9YjKJ5PfF+8sKb9iiKXRLXIWWJq2KMIvd+EdjxjRe2G53AC+sT4t1M0naz0fhlZ2Q772/iaBa67Vs71nCS" +
            "eofSjL/D3PTOGKhXH9uB59+3k/skJUF95hl1ezr1SZ0lGLUhuYmI8u6kZiR2mNQTEnvj5WiWplEIizedpQtspu2FE8hJec7CmcUJ" +
            "dDaNPFigeGk6BH8XxXHeTjqul0x9+759E3tuB56ALzzoNmmzCp2p7bo4/db+dL40/Qimu6CM04amtKctoJ+BM9Y7KZmnddv3bkJe" +
            "lxaPeOnnz6bzDu+ZPgvSKfYsjZQmJnESx7brzZJ2aw+TynTEZdYfHTWjNGUjSmNYQ9LGZWavd2wcLxuNDl1hxg2sA/ais8Ero4W8" +
            "aJSp9OWkmS280lCei1ZpJ3vYiU9SGEU9mdoO0q5uNvZJsDSTWbE5ytF6R2qr1UIqT+2Q+Atp6nQijDrtJvBqEvmeq7BGUAz0Mulg" +
            "VbKVa76gpF03zXEUB4sCPW/sabsJyy2vqE/GKZT1iO+uFn6Og6Z5XZhyuMip0dwrkfz508bSpFxrmEEUknuZI5/uZUyw1TTpIAtk" +
            "ssejscM74IxHxVHQAgQe+4hmKdUeIYxgycdRnBX8UweGgpSU6rpZECbtPVQWzXHcoTSplhPew+oy834UNjacNRtfQ8y4kQ2rIU8q" +
            "jUHqp3YM3ci83CwR9gXwclm/ACfFHnDXvWEmBDS1Sx9dMCegCUqkxEFLS7H/oqJ9aY5PKZ+y1hfZBNZIay6Ny3wgi80LXWL+1akV" +
            "piG18tW4Odq33Y0txLgeTFG2Yd3tkU/cRYTSmt63zadPO1x7hhHyvx/dEXdpptF0ZMcLWYdNowSMJzSSpJ7z/r4DRYAKP8EIXTJv" +
            "P+1UizDYEOg5WDPzLfhw/xnjQwUfHmFGFBGU0Re4YGwKink3iRYr6roj8WRWNAlg/kb2lsZReJOJysiPnPfFolX6TVIHqJfuJjBW" +
            "qhtRAO9ie9pBlDEGMrcnnuuSkCmeLJH4vjdNvGRZGoYkDQ2cnQecNUqFedpv5QaHPn8Uv61KB8gvQKXNjRek6VFheNSKlWwVG0G1" +
            "STI5EJNs88sXuW3mJgZNbcEuUP5QnlHyhXYcR3dg2eacCwBoYboLrAfmI1v1sU8qmQ657GVZIX+c3aLcmo2vxcfHR5+JzZ40KoUa" +
            "nM1MR20QDLrdzKsxLVyluFb0bW65cgW9NAFsRYvHl7hkl1vS/FoVU6MgC5um8zL4IxWsgtB1MmX1vCUqVMimRAXKpZW4RDAHkElp" +
            "IEgJikv9wwy02/i+zjmsTSW3PiLpHXBeFSPwOaMyrF50TgFWYn+NtYRxiDlV42ZJ8kFPExl1tEptMiGpRqjO+yobMnafrtqQp8QW" +
            "a/vC3n/aHBd4tUoHlLgQp5/Bb3MfFJo9Sj5GZIVIKI1t7ERMpsROtX0DbIWeIzXsVOFuwicryAqRK82/VQX9aN8mdLy16Mgd0TJL" +
            "E2e7t9ieAHs5AagFqtInSWqnnwO5m89l1fV0I+TGvriAs0fK5uJlVdoramxha5tiWtX2UjDYPhd74iCSoS76Z8r/ZjpL/SiT1qIE" +
            "aYUvVCq4br6g+7wkjeIifF+aaMi24A/EUFTAsiF/hjqjE0RSQuftsRcnad2ZeOAvSQ2wXL6S9KlqtfOMtd4ibY0SFOaU3leguYqq" +
            "meVB/EgBoU1pnGxLK/R9uJmHjudTEiaoz2z3T20xst6TVW/0hZS91cTMPZia+QLxdIuj5edSG8qqo4q4G3RZbCeTT3dj4gqdyWR2" +
            "FjC3Sq78Anxb59OxFFqfFUq9lHpTXO/2Y1bxUciVt7zGiiOct5N0kTlRY29O3A5GGtpPwWena40ODkVkGKZg2AwX7zut/hSNpHC1" +
            "9goQTsQMJWOSEQJJx9XzY9a6YLyKoSJwrNAU7z0VgcOGgf8zWy0UUAGgX7Z+WYU5gK+iOK0QWTFIFmcQfAxlFXPkyfp7n+ZRslcq" +
            "RQf1CDqrZWjh2L6jIb5Q6jTqqWc4gwZCaSiu8VkIfk/iupc4DZnz1rkOZVaTDMmLSnjI1gZSPBt+w1lAYs9pA7iY+XaM70lOhC1Q" +
            "ZJPKc15hNYiFQbkkuYO5oiuUsWxMgBu9W1LIVnj8SSitmAW3mNq6J3ltwEKgg1LSYSUorwPTU+Zf5flfc57f7N6u9WVK69Qo8DsF" +
            "BFWcBONtjyNnliyE79MqcQHH1jy7Ho3HCUnbXJE5DkkSTN8CnZedrVXJ+bOAuJ6tSR7qUxi3vmAweiMEbFEIKKDjWguXG46NRpAa" +
            "CslAuCRxWA1WsN3crTdz67q+u+UXB7t8N+Rgl2/s4MZC9wDUseK5Vs2eTmvdg114xY0TJ/amafcLTdOt7kKdQd/A3J6Tqp0vQHaS" +
            "VDn+xlKbLXPPbKjG4fHX1sKeen9O7ttq/+Qne3jfd978+O23xB78+dj94WR42Tgb3r3/7ttvE3VHHQ3TZ96vW+9VA9TA5CgKbFAL" +
            "qoPbOHXXrvvRD3b9qevu7ZsAbsjITgiMzXSiQDWmcfQDwLUTt7K8aiBEs2/Iq5nzHnhjY5u8qAltq0YA3GODdroZElDzMbbf2t/b" +
            "2280n7/Ya7RgpNMpJjbbcnL7jozajZekMXIarWdPieu03JZjj162xupSEKp/9ObkzFJT4kwc4vu2kzT/7Aam7LMZHfZPvutbKiRH" +
            "oFGaz5qFXOjWckEqAuBL84akA5/g46v7E1dTceS6MQ5SKyR3ykmY+ubZLBiR+BjEGVhRpTt5qrGgCw/UmMUgoM69aointvrq4lRd" +
            "6gZoECu0ukNY4/BGC3XQMu4wtWPkZ7UBvaSRa99blBvYtFza6ZGNSoWkszhUXBzg8cz3f01A2vQdta7uQDMaTX8DMjeBxGYpHRvQ" +
            "dH1pBFiAdkC70nQTxNkhYO+e68YothKrKzrUkh31stlqNxrw/6oO1v00wj1FzOMz4FPXDdDQ1q3VZXTReO7tw4MK9UBiMRSm7f4r" +
            "c/fGkFNUA/5nqrr+8NAwQOSwd143KdW9enLQranX0IBjdbWF+kRtq0/sYNqBFg7w2U/xsYuPN/Sxho8/ziJ8qak1ePlq72VHXV45" +
            "12Alv/AJCBeVDMMdGSB5sRFHPrEYMwOrRreJdXVtgBFJYED0GXh5Sh9AP1mqb4eOjWwSJSAxYLAi11InIDiqQS3PpQdWzAphpQzb" +
            "BZM9DO1pMonSC1AL99bY9hPS+WI8C6lSUWiVt8AKiZavOSzZgi37AgNdbXfjIqTQoVQE+y8WMRaTaBaDzNVd78ZLYZJeCHZBSmDR" +
            "fCkBKwAP0NEu9eWyON7XaeBrYoiKSpWc44PltGrC+Na6//h3/+G3ygHaYAUmAUoC7GsdpwNqEFO7B8ygS7k4E8yl6VxbqnnfGJw/" +
            "xPahbyYmU0uin97JZPnHGYnvh8QHZRbFfd/X1KviGK6BycAsD2xnohGrS0y0Uod8E3xKw4vbNoeDfqQ5LKLn80hQ9MVEvLGWs43u" +
            "+CDdJ2glb21fzuhIk+9IfAZGOiueFcHQU0Nf5lzmI2ecJFGJxxgRf7LqVFlggz+Bi3ROTb+mG8mN9VPXavTAqLRBrRi29cZOJyYY" +
            "ae0n3QCEYCU3VNfQ5LEfRbFm7z5r6DpWoDn2L+H151BhoJOkxNfAnokm9cOaoGy9mj6k3E3TYdD5Ovg2PE1w2tqcLsS8Z07AbJ1m" +
            "Eq7zkc9L6YDr7nkFMOXgSgyCHgggjjWvInJ4hqb/UUXUsVNkPn2xFGKplmf66h7nKcYnhv42ih8e4M1OZ4DSfhIpUD3nH44Ah4hG" +
            "gWdx9nfgsUV3Zj9048hzeyY6BkOpWM/UpEEhp4GaNUkcA5MQWaOw8xFnEXoOGg0TGUFyU90J6NE7WtKDlqEydJLX2NRbPhXqrGqp" +
            "MQKbTGmnL6rlXFOZY6vqPbBHYBcISh6VGJKjBicGWEo4cNBU0Fiq3iEm1YZneM5GZY10ijoh7QD3wBB0YlL4YOZo31L5+RY1V0CI" +
            "JRFKAXw6xFgUzKmTMIkFwE5hJMkGabRelKX/JlqjrnEnqta9OBh1f3GwO6pQudGUUViqjviWxKIFtpcH2JZFokUy38SrUfCLA5il" +
            "te4f/u3/gl5oua48irtJBA3QUFlX3QFEoKFh7pkEoRpFBDsqmAWaz60GL8fGRnMLRmOHzRkz2HAhSTJd+QRHwGGXMEmJC8pAkI0e" +
            "2QAcwcEtpJ96wLIhIB7VARQFSBsX4NhMwMc5h+VAdAFYQ1bzsiyltDsKKjKjMQfkhww/l4VmucRGGPaQRnxPtYmbrQhCFsDdIJnQ" +
            "yhxtl21ZlgvoCkGI1DONf72xk/ca8YXyp8oksbg2Ir4JhmRGygjuiCE4zv7oxiUWB32sAUBywPa8tqXRErtghXKdV9Z3yHnBLDiO" +
            "mXd1RJtptwzwCqvSgRRIHzaA0BJdmT4Jb9IJdp2govdZtPkCDxVooREWqFlY9DdIjBN08BNtnQqgpl4+74EnCbQrcdTgWpfNvg9L" +
            "CMOg5d5ELkg+j2eoKOuQgwsDgzSzZeCmolOVB+6fihmrLEc7YCxXWFAjjWeg6kqC74UwO5Qmikj5AoKmILh4xLUoMhhyZw04/wR0" +
            "scbA8PcgnOn3VAZVHTmig46iF8IoXl++ObXUA3QtM+BHfXsUYyKQIk2nY6gVxB0FakXpHEyaxbOGILnN7sFUVEpmo1p3EKYxUcB3" +
            "A4tk00OL8K/twjhj+Nc82J0W5RzkH4Mvog16IEvBlEwvwdgUqoFBF+YReQy6wph9ewTleWV6MArnh9h1UEeyCCBLlyTTfPjC2qe0" +
            "qyl4EjR7icmPM/CRXRqkg5lMwSOBXFwiPJdZU5gI1Zh+y1cK5oIBBDqkTSMDF3tii4FJRJcCW7X1I06wthixqLJ20MzRTetZwbIV" +
            "IPdENMYyWC9pdHMDGoH1hcG/Op2PVXsT4UrGChsGuBD//je5wWBsIihQ7IgfYeJkD7ERxi12XGlxcKcZ7NF//o1yzAMWyE3AuQB/" +
            "REfIJ+iNMH6GJ2T3rlrQ61yg6HjXhxJoNnhqQI71hSSiIIS4J2YUUvMixwUQAFm0OZOS1bJUQXwYWJ6O5XoqIg5AwnkJbFWGIazY" +
            "P/7df/lrKIbUZkVAE/VT0NdAOPDW8wUCFEornDszPxXLBDULy6bqy84mW+qFoDVhMrNR4KWWndyHDgAq0J3mNCao6o7I2Ib2M+KO" +
            "1tOMrTRSFoVrQzmmxLjNgJkF0PrIFIrcQtUJ7zJpVMo9AD9N01Sp7bHvbC9VmJk/Cb/10skAmwVw+pYTmFp+NhaDLQbtUO8UtGyy" +
            "VsuyqsJmzctAdg42kKJXlakfJZoxkoNxciIQxTRKTJXZAXl2LOZQNb1YXcrY2KdGhoMToNDa+AUYNBo1sUTcRMCJ2Do2YRU0d2So" +
            "gEs8ESfhUSeOZ8zpLJlox8AFonEtNhLgARp9SUwyh4GAWe5dLTy3nZiea8AqJNRKAkC6bl9dg0+JocRDGxrSlwbhEiKjfkGsIQkU" +
            "G/2SSLEjxWE2higYshHUWurCSV2yefwI86BQAEbpRD6HFeVp6QbMAcPx4FmpODjVUDGGrG6Y6I90okDAVeIC3ZF4hxNELzD9DFs4" +
            "E1bFmVDp/hLIDriAuOqTJ1JSELngGEGqwBVsKnMLiQjloGlOSf7CyWmM7gX+m5e9wLnsJKo8GnhH6Y7uEDox99A3DbNiaRoccyNI" +
            "onQtDOQ9uefs8n1IfTjifq/uzGFMOyo8ZEPAYb0uuNvY8mQlJY1Sm3kHOI4EFhiWqAxloFNdDKNUJMmLGIC2hLqhzQp0y3vp9bQs" +
            "wcUT+l4cIeTdyVIdsAk2TdN19EatUcynou9ohUBDT1U+/LdEARdFTm1TJ0f5w1/+d+USe4T8cZDSU902uKuYBDwlu8rqWXQb4Ucl" +
            "H/6e0URxI+WQRzHBHRbsv1IsJg4ZeW7UruoDAWSF2FMFyaUTWCcxA3uquRiSBd5yOVu5gqOoNKF49rGlj5LPRB5qIssnF0xnvWSO" +
            "iTOxq0QzIMk2kulQ0cwiv5880/JEdRmTy3orC/Zz9WgEVubdGcBngguDXoHrDGA2KStnPQNBCSj8L78MnjzRAiGNFpPGhwdopNt4" +
            "eIAKXXDXimgeNb7K2AdhcwHaM5uhsJOlRSzPD2IiVvubf8PCvV2YgY3MSGW9GOwFeA6C4a567GW3Ac9CCkjLBAIadL1ScxTuAelq" +
            "3QIrwzx3YJKFXlj04IiTUdlRkGwf/ke0+/bkuyy2kIO9TW6DA4uHPWqM3D216Nw475Eaf/tb5VQSuh8+/A6WnqtWtaASglwlIOQv" +
            "5HC9YCrDCNshSqRQTvOoxxPFyhQ8TcX2wfUC/AV4gMwdH6C6yeMgrPomRyakrpMCMihYrMJ5oI6mqHLxi6LfQ9cA/Sj6GqDrW+Ou" +
            "b02RqCTwCB9U7ujgevXgP9C1x3h2RGtJe0Um3S3idKgpNHUS+SBDVq1hNBq1smOw1Vz54gMbsOX/6AlDA58xYajdg/9+nglLw05m" +
            "QeYHRkGVsNASjwoL62CNi7V+hrJMyMw/8iNQ2cj4bfXY/onEspJnwb0KH006dgCu2n/9G+VcScDzB2009mDk+EJFwp5++IcEBCPz" +
            "5BLbv0W/j06i0ukbAmCGmYIwfvh7ZIMoxF4UPANil9w/7vRlcULEGmyORXSzwf1z0eUxNrgxDvVhYiJ7eqkFLKUJD2IHX0bCnVjv" +
            "aM4CxGgSzpcN/IaKoEM3VVx2VkJmHRtsJxUAa5Q9xaSzYZLJRPb8EvuWoBlcfsF8wDxAyjMwaFfhFHLf48vc9yisQ25M0YAi2Tav" +
            "C6cptakbS9NFEqXX+Tlgt0Pw0PNNNsMp7FFCJp0BdH3ALPFBQ+wYcTh0EiLZCWqLKCaJcvvhdz6gtdyzk33ReBZe4uEgO0NBjJjp" +
            "PA8ZWKxwOscpaTFDy5mPJUBIZAn/6uFhscQy0Qp+4JgjKqARRBRZRo5FAGGkEzwBi7QYUCSksj4BaS9hMLjHGBu4wW23XUO02IYH" +
            "g7XShh+DotI211EGH1Abh2MUPYK2w/ZZjVX3oZ1tfyL5jcyjaWPQLd9kMMSGXRt8fBLfkhiNcJLaAYi9UXCMyjXzzOraAKoBdxY2" +
            "0QT6RcV2WkDnvCUe5mTAmMEDPsUdPDzxaJBgbrJjN9RL55TvqedFB5jiknHkcYXsRiao6DNUiZg4jZLkw+9vwZwylZrBcXk/DbeD" +
            "pd2h0M70LWahsbm6Kh6akG1Dol4bVyrw+SyIIOeCPWBajuYh/Th/wTx2nBIE0lAH4vH6mkJ1YHxVWC66SY+HNsDmzq8a12hP+djA" +
            "UmGGZWF6T41CbsJwvPOr5rVkk0Dqf4gA3PNtKJhgV10B9Bz/LyTtRN2oTDtVoO0CkoPGGSnFwAo069HG3kS3mt7m2ZxmPX48N8+Q" +
            "CNfjzgxkMpJ945E7DMPk5uzRQw7Qqny8YWR1R8XIJHY7ynYw4K3kErHexFtOuHxKmR/E93shB7Xnw8PP5Bd1Mu7c6NaUXBAJ9hdx" +
            "ifRFA8sR3kX/cDAcniuX55f908ynmLS6Er8f7MK7gBhlgLLOmVqFn5xCIrju0ucMX7psr4DthGrgC5a8jTV9ivM6XVSfShR7N15o" +
            "+ysO3KqTsrr/+qfyO5AO61yPf9b+BUzscJ2L8Sf1I6jzXeFIwABp1tbehNQ8P1Bb27iLAx0MAQlSRzvoqUNqgRQa56agHXjsEAz2" +
            "Bl+i2Dj70Dxr+4j41JWBpqGlzJFBL2bAfGi54ZVdqCxQUKU0NmwprtcjoBdegzH48A+wxlxNiBMRdIef7XSDGykNS0v0PHZRpjL/" +
            "pIoRMG+hR58zUylVyII7fOkPRixcszaOidHKFX0wF0ELtrfLGacYfSmwTGVkVQRDD/MozZpaUuRVjJAedKoYH03PRlegW6bdqrr4" +
            "hBhwhU7MYUS7QHb65Vete0bCySyQV1cEcWhbJbbjfumK88RitzaeA1qPRYS5XX/2mZkX4e887koxPfwR3hTTb3kFClABqUiGfitH" +
            "6kuXhjXXOVOHNFgAxiwR/lTmRvETpPa9H9mu9Rl+yOd5BnQSYvb6+QgP4JvAF95NqPHB4eHAP6Grsyy6mADqjsDTXevvZoME63RD" +
            "KEmWwgcR8yqGg1CHA7ZgzkYhg47qU5ydrXyWKlEBM/BzSgpdzYyFHx6+5NEqTRVGpbRlQ5QsIt5Ts50qmf4uwcMWm5YgI4JMSxoI" +
            "/vB7JPKnkU9Ekit8vrGHAWfiZsg9zXcwIiuVvi1AevAz8hY/Ja8Xj61hS4WDa2mpEm7ebKzTk/qDCkFUasAOo20b2McGUuk1cx2w" +
            "YuFALHe6FiL6l9OEaksbgJgLzrumJcYcvKRkp9J4GNxr2VBc2t55xJMpwgvqfQsgkt8kwy424m45+2xBfY0/6FezjbI3H/43c8uR" +
            "cobaD5nTnc5cfL3En3XONqd6pb+drchHu9xlZEO//iqBqdROBWQ9KjkUVbYdqFGxPaTu2DnOwm/UNuErucvDKrxf1W/1thSQ5sPv" +
            "I1B9CfrfwGD0GNI2HVfA8233wghzV5JSR5XbcQzWPgJf33CtxILqsqe7BpUKYvfsx/Doz4BExQpvgzzFO2/3MdipVm8m/hMBlri/" +
            "Lm+qKyFYeqIIntsKZTIt97Z/dNE///5oMHw7GPaH1pXaPzu6GAzPz5Rh/7R/cXIO+uHw5LQ/lN6zIs09pQlNXBwOTvtKs7XbbGWl" +
            "m3v/9/9AJvxTzj/rX/ZPT/pSeyKlok6T1fm6/+riZHAqj+H03dfvIOX0/Fd95ZvB6et+OfHs/Bua9vW7vjJQTt/9BbycnF0OLs4G" +
            "l6gSByfK4dnbX+Hj+eW5ctn/7gSejwbfmMrRyZliHp6eDKC4ei192wVY4dCPEgyRiPioOFwgndcGnYtWSxzXlk9rpxMQqUSqvpX9" +
            "00ttDGcBbUNYKrnhjvjMDK+o+SiT1d7WZHHovKH4pxyxacifkNAZYfTFI0Vq5bNnneRVXLafNODXT+QR7BKPs+MfBL/YdewIj4Fk" +
            "Lwb1KNqNZeF8B5lPL6I7WJ28ICihvGzlZxj8C+TKg7kKtlNj5hUfiweE52bWDf9QohTqOaLZVCPXtg9B0d6go0fiT9na0Onh0vSK" +
            "SfrnhaaKERx6UQenRBx0P/ynUlRGjspngW/O9oGILWMGoR+bPTwE7Jszw7EkUcXtL3BHrX4c2/eml9BfzUE6T0liJ/qTJ/mLMGB5" +
            "SnuFswx/5oiwtGPCSxyBSsZ1ge6jXo+n9XogKyS1yHy9pGRkRrkKEkvmcANWx2Ff6AX2HLRBkDCmr0PzMAjvRwse6iT9U0XDUZI+" +
            "/Otz5aivvD3tn52A1pWD4vmWjgL/JohiyqHx9aFWhKiPnb7PV1qEyQP2kvFvUDhTX4x/0kCkHA1jp5BA2b8enFycb4JcQPgyvMxg" +
            "UKGxN4ML823/63NFK+BH/ZHG18ZXESdlh6X46avR2mbYgb/Vhh6j/em7w4tz5fTDv/uX706O6PJSCzocvFFAax5+SrSbCsBjwW4o" +
            "1IP//hjB7pXFhpU57B+dXwDO+MNf/m22o1MOgqPIVUXBMX3z+mODQC5F2JmKxsEoVLVN0vWLny3K8ckZDnhUaDAkaaktUAprGWDV" +
            "PNErojBo381HPSor6exiWBFpd0ET1ro7St/1qGcTl2Ppqx0lNUEA9oabTnNqiZl1rfAON2wk0PMkqAsACrNNhHyHdMPHIeeJ4nz4" +
            "ne/MoCr4ZTczgNGRAo50jILq2vQTJGC00PMndlt5A0uO+7ierVhKUQiVP/z1b5VTZPEOiM3vf5x5kDPGvTUoStNpiSNhU9bDcGnT" +
            "2Ua65rDlCk+lbviIjdNS1ZnjHheQTDsuf/p6lcGN69JnExzJYHB3TS0okFXCE7JFwJqjFQERltKs6MVGwmYTK5umAXZxxSzyBtAW" +
            "+pujzVS/4BecPNocbDiLlVmNrPzDg7YePHyMHWZHf9Z+pMLlXdX1LcpsOOW1qZNMY23qRSq0rhuq4Tb2BNpmUx80e13rfj3Vq8+F" +
            "UZCWg7qtlpFvYmzHI9mGxPbM9xEMwGBYPd1ic2NjDF4+6m5AlwvwItuBQefQ9o0yyISkDJ+S7JEucTul42Wqi6Xgu4HHS3z8HO7U" +
            "owqrDSNnrpwIr7UZzmGJqP7e2jc0MdsvocfJ22L69MBWjvpWdjGkrOIuxudtsJQ2JqpPYUlgFLcN/nh7ELKfmJ3HWXwGImftFDH5" +
            "yLupdf/wH/+n+LK21X3L7ZQS5ibscnD4WjkcnJ4q/cMhA9/Z17b03rBa92sS28ossBV7BLbOjtnXtkoA6NtQAhvHB6ZMYGIjA7QG" +
            "+4pGcb0Pv4u9yFAEyxkKZU2onJtLovgFo0g/4z2YsqBfYesb4UZp+5tG6GjJLLRSKJ0LCo3lTjd/OMpoKYLm+R8eqHVfISvHymDu" +
            "AM01c+5jaxmO2WCp54E/AD/9NltkcUtSr1f4xv4JfmLPrjaSUg9oKl5yJCV2aeJNMbFGE9nFR5JJxUuxtFvQYCrrFy/xwslB3znX" +
            "HUIhANg0p6cqSdIe4uPJEeJumrpD71WipyMO6OcdUOaS+lTqTspD+GKiSHksA9TAhuXrJGhEEhO1Uu+7cingdg3HnUiFAPXhGmNq" +
            "AfzR9FIk6BJ90TzidXVvBMG1FZjJ1PdATOt4cZWNYbcr9Vf9M+RcPGE4+GZwwZ/f9C8+/BWNG766ODmlCTSM+Kt3Z6/Z7yn9BU4f" +
            "XuLDcHA5ePOK1j1/d/mOPZ2dfyMSjwZ/wR6vufOt0QFcicNqgV5vXgOkCDCWrO7cS3Eq+z45CdnlOJtmRFEday7bJMrsCxYHQ5Vf" +
            "pyOtCDI0TfSSiG6EfokPrAVV7eQdGu61BVlSn9m9PjvqLnWmdwtDTyaEpN8F/rrYIw9bwi8InabNjXs9uweMB/pNagjIYRTg7Yfi" +
            "grF7lonQy55afMt8HEeBCAFm2wlXrB1jfq3/zFGeq+sVs/8xYZ75NC9aMMe93rbxH44zsmZKVrzXWxcKYiAkq1c29L2eRuNE8ymU" +
            "DK0iAxogm3gdSQd/2Td0KK1XVM2ol/1XGIcH/5N+EIqCwZWOSmWSfqidaYCtnlnDx2Cq+m8GZ5fnYGLOhv3TT22aNXcx+PpkeHlx" +
            "jiM97Z99+CvW9iON4v1xldM+OilM9TW9cEYVnQnruKGIMJsbivCAAQx5TWers+RxmpPqMM2Gvr7pn55fPNrJaoevzy82EeLd8N2H" +
            "37D9mJUiSFvA/hpez+dazY57YIUdd2dHaA5QPFaQXwymG3MLRPwKkq/lA8Hz9QeC54UPJUEapAyxASH2SnX8CwvKGIykcQP/UDfH" +
            "sqyWvoA0RtaTs+Pzizf9o3O1c4Nx1SUv81yHIiI8ktV8SWsi2S9ODvu0Dqcy/96ceJZbbzbpTTFe12o8eUK8A9AETPFgbTK/It51" +
            "MdoPzfBJ8NxMPyyrGLWo66VVYCdY2DIholdZq2j4MHInshDXr8kCClZkrbDIOO90jEczMkI9PIh3TqGeYA1EHQaGb6OxcoNlQtZL" +
            "jzZ4s9prm2U8wqzz3srn18UMelYQk5E1y+RcaY67QvlguPuc5RZpV85l/tFq/qqMrQQO1wscaPBHmtziuULpfawK314vC11Fo5fr" +
            "J4ae6NYTW6u18xDvY2p3fT71lD+bxp+0JgIYfxvF7ynSQkhOb5yrZVi8gIbZBUKXeMwZkDQliQykWcbBbtZewYthntF3fpLHXrBt" +
            "gAKmaSLOHJJUw2f5cPHq9rROb58QjlpWDKCwfi3ChK8i8K3tUIcUCgzZMU/WndCH/I0uqYjDiat40F1JLPWAujBJl/0icdCd4Z8R" +
            "AiH6eCM1OoaY9Q2JU1CqvlU7ZNdY7XYPjiOWh7+Mroe2741ir0YdJO8nSGk2seQu7aLU0yXfEBPNvIp88DCbcuUWVqa3d3pRjOmH" +
            "eJu2Vfvq6OWgf/ycFn1r45/6Ca3aEO/qXtsbY8uK7tb0MGgdN45erunhFb04IREPtBC/69yqvaJ/BYTWPPVCMmSuMsbwvHAWzRKa" +
            "8y29/pv3v5u1Vzn0N3xzSL5cmdEdn6zaxS+Ur4yvvmqYjcZ6YrN9kqrZb9lsFZGOj49bh4cfswz0m5z1fbrubhDs3sP/yS3sck5V" +
            "DRBbYNwe/Ci3QDBK76bZqPW6VMxHUfQe4wlhYtVA+NuJMyGBndQDD/yKJBqndScK2tF4DOLWTqYYv6SSXGOV2snH1uty7z/Z4QKH" +
            "EivcuuImjBgfzIL+eVlVbPnWMcxUxzBTvX84BADHT4ruqBhEUcVNqis3fdq35JWdkGf7x55PhNIZ3acg26hvLsk8HYROBIwFHiSh" +
            "TxpMk0G3EUBCgEcCUXpWo+Md0MriwkBvx9prPX/2QoeiO/z6G+pDHk7s+BAbA03FaiSzkU29Q8/wdlglvL1fHm9puNS7N/AGcZ9f" +
            "VLp7G7pmkNQpBFONURrZGvScH93NAnQ0YoifEB1FdyEerE7MzNNebrjilBPIj0aUPq/gQbsCglwbC0RP7bWj6Yg/Pkz/9jBeV26v" +
            "vd4UbyazzUlMxta7i1OeyxxweNewdyzg8qFbSAd4px8LaitXlmITMbmN3ktNsNb1lRuMpU8IF6vfom9/TJsict3FLxjpNUvsE8bi" +
            "d4eWyw9zF79jfPxDf/EVxKZv/cWHDzgQ+8mTUX5zFo5kU7VHdmTWXhew5lP95aa+8AuSjTeeZt+ZbLhUgH1l9XgzUGiL71DZ8eHN" +
            "n6LyI8b516gsYeWDVH6P6Zb7R7hUTqA7QTXX5EUtKFLJOY9vTK6955PtiW5BH77tKt0oO5fuZdiykTioaEMi8JzNFjVWvqWcX48s" +
            "NnCXG7kCd9UevU53sWkPlB4LgI49+I3TvvuDjdeP4pfVmjoiMHwC9FcNfhZPL36BvOn+DLHFuHF4WakNLTHAvLGZDFOvbnWCJQXg" +
            "u/j/ft/qIbsTEzd57PyCFbCMVTetPnJTZn59L7ZhqZM0nSbt3d27uzvzBo+0ew7+VY5d8RdEfkh21Z3jb4yrvvHOOLrmt1i8BRPt" +
            "wRBs4NkrL0AKalh6R80q4t9dN3/AK8DW5c/SycYC+ICn1Aktda0bY9vqw0oABMa9RtKfTrXD469BsUJL1jtc/D48aWMbOGJkHdF7" +
            "7kUbNPXYWgDV3qH7c7TsvAOhwgpDmDVh1/3xuyNn7KK/L2fZHYz8Dl+BAOhVvrMOvX1Rm0k3ZBdv5rMsi/5llB7/non/sRZ19ebG" +
            "J082NCOu9tMF3Liz45B+VZfaSoibiHhlJG4JprFNf2EF+akX0JviPsmO6JINplfQju3CTY4bbnH/ea8f/jIXg2MbgZfLroCds08X" +
            "VkQBXERv7IHeVPC6TXQGAFjCyyjGTdVbusdI2Nbl6nbg8gsm0Z2lDv/gHwtifwEIxJH+laDdCQhR94v/B2oftEw4ggAA";

    static String html() {
        try {
            byte[] gz = Base64.decode(DATA, Base64.DEFAULT);
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            return "<html><body><h1>Falha ao abrir Caixa da Loja</h1></body></html>";
        }
    }
}
