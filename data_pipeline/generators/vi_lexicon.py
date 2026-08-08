"""Từ điển Anh–Việt viết tay cho flashcard.

WordNet cho định nghĩa và ví dụ tiếng Anh, nhưng KHÔNG có tiếng Việt. Toàn bộ
9 000 đơn vị tiếng Việt (3 000 định nghĩa + 6 000 bản dịch ví dụ) phải viết tay
hoặc qua LLM — đây là phần lớn nhất của khối lượng remediation.

Mỗi mục:  lemma -> (định nghĩa tiếng Việt, [(câu ví dụ EN, bản dịch VI), ...])

Câu ví dụ viết mới, KHÔNG dùng khuôn. Bối cảnh đời thường và công sở trung tính,
tên riêng hư cấu. Đây chính là chỗ đợt dữ liệu cũ hỏng — 3 000 thẻ dùng đúng một
khuôn câu — nên mỗi mục ở đây phải là một câu riêng.

Từ nào chưa có trong file này thì generator BỎ QUA, không sinh thẻ rỗng.
"""

from __future__ import annotations

__all__ = ["LEXICON", "TOPIC_OVERRIDE", "COLLOCATIONS"]

# lemma: (definition_vi, [(en, vi), (en, vi)])
LEXICON: dict[str, tuple[str, list[tuple[str, str]]]] = {

    # ---- Đời sống, nhà ở ----
    "vacation": ("kỳ nghỉ, thời gian nghỉ không phải đi làm hay đi học", [
        ("She spent her vacation visiting her grandparents in the countryside.",
         "Cô ấy dành kỳ nghỉ về quê thăm ông bà."),
        ("The office closes for two weeks during the summer vacation.",
         "Văn phòng đóng cửa hai tuần trong kỳ nghỉ hè."),
    ]),
    "sofa": ("ghế dài có đệm, đủ chỗ cho hai người trở lên ngồi", [
        ("The cat sleeps on the sofa every afternoon.",
         "Con mèo ngủ trên ghế sofa mỗi buổi chiều."),
        ("We moved the sofa closer to the window.",
         "Chúng tôi kê ghế sofa lại gần cửa sổ hơn."),
    ]),
    "towel": ("khăn bông dùng để lau khô người hoặc đồ vật", [
        ("Hang the wet towel outside so it dries faster.",
         "Phơi khăn ướt ra ngoài cho nhanh khô."),
        ("He keeps a clean towel in his gym bag.",
         "Anh ấy để sẵn một chiếc khăn sạch trong túi tập gym."),
    ]),
    "umbrella": ("ô, dù dùng để che mưa hoặc che nắng", [
        ("Take an umbrella; the sky looks grey.",
         "Mang theo ô đi, trời trông xám xịt lắm."),
        ("She left her umbrella on the train.",
         "Cô ấy để quên ô trên tàu."),
    ]),
    "garbage": ("rác, những thứ bỏ đi", [
        ("The garbage is collected every Tuesday morning.",
         "Rác được thu gom vào sáng thứ Ba hằng tuần."),
        ("Please take the garbage out before you leave.",
         "Làm ơn mang rác ra ngoài trước khi đi."),
    ]),
    "bathroom": ("phòng tắm, phòng vệ sinh", [
        ("The bathroom on the second floor is being repaired.",
         "Phòng tắm ở tầng hai đang được sửa."),
        ("There is a small window above the bathroom sink.",
         "Có một ô cửa sổ nhỏ phía trên bồn rửa trong phòng tắm."),
    ]),
    "homework": ("bài tập về nhà", [
        ("He finished his homework before dinner.",
         "Cậu ấy làm xong bài tập về nhà trước bữa tối."),
        ("The teacher gave us homework for the weekend.",
         "Cô giáo giao bài tập về nhà cho cuối tuần."),
    ]),
    "hobby": ("sở thích, việc làm lúc rảnh cho vui", [
        ("Photography became her hobby after she retired.",
         "Nhiếp ảnh trở thành sở thích của bà sau khi nghỉ hưu."),
        ("Collecting old maps is an unusual hobby.",
         "Sưu tầm bản đồ cũ là một sở thích khá lạ."),
    ]),
    "poster": ("áp phích, tờ quảng cáo lớn dán trên tường", [
        ("A poster for the concert hangs by the entrance.",
         "Một tấm áp phích quảng cáo buổi hòa nhạc treo cạnh lối vào."),
        ("The students designed a poster about recycling.",
         "Các học sinh thiết kế một tấm áp phích về tái chế."),
    ]),
    "vase": ("bình, lọ dùng để cắm hoa", [
        ("She put the roses in a tall glass vase.",
         "Cô ấy cắm hoa hồng vào chiếc bình thủy tinh cao."),
        ("The vase broke when the shelf collapsed.",
         "Chiếc bình vỡ khi cái kệ đổ sập."),
    ]),
    "birthday": ("ngày sinh nhật", [
        ("My sister's birthday falls on a Sunday this year.",
         "Sinh nhật chị tôi năm nay rơi vào Chủ nhật."),
        ("They sang a song for his eightieth birthday.",
         "Họ hát một bài mừng sinh nhật thứ tám mươi của ông."),
    ]),
    "classmate": ("bạn cùng lớp", [
        ("A classmate lent me her notes from Monday.",
         "Một bạn cùng lớp cho tôi mượn vở ghi hôm thứ Hai."),
        ("He still writes to his old classmates.",
         "Anh ấy vẫn viết thư cho các bạn học cũ."),
    ]),
    "pet": ("thú cưng, con vật nuôi trong nhà", [
        ("Their pet rabbit lives in the garden shed.",
         "Con thỏ cưng của họ sống trong nhà kho ngoài vườn."),
        ("The building does not allow pets.",
         "Toà nhà này không cho phép nuôi thú cưng."),
    ]),

    # ---- Ăn uống ----
    "sandwich": ("bánh mì kẹp", [
        ("He ate a cheese sandwich at his desk.",
         "Anh ấy ăn bánh mì kẹp phô mai ngay tại bàn làm việc."),
        ("The café sells sandwiches until three o'clock.",
         "Quán cà phê bán bánh mì kẹp đến ba giờ chiều."),
    ]),
    "salad": ("món rau trộn", [
        ("A green salad comes with every main course.",
         "Mỗi món chính đều kèm một đĩa rau trộn."),
        ("She added olives to the salad.",
         "Cô ấy cho thêm ô liu vào món rau trộn."),
    ]),
    "soup": ("món canh, súp", [
        ("The soup was too hot to drink straight away.",
         "Món súp nóng quá, chưa uống ngay được."),
        ("He makes tomato soup every winter.",
         "Ông ấy nấu súp cà chua vào mỗi mùa đông."),
    ]),
    "juice": ("nước ép trái cây", [
        ("She drinks orange juice with breakfast.",
         "Cô ấy uống nước cam trong bữa sáng."),
        ("The juice spilled across the counter.",
         "Nước ép đổ tràn ra mặt quầy."),
    ]),
    "butter": ("bơ, làm từ sữa", [
        ("Spread a little butter on the warm bread.",
         "Phết một chút bơ lên bánh mì còn ấm."),
        ("The recipe needs butter, not oil.",
         "Công thức này cần bơ chứ không phải dầu ăn."),
    ]),
    "tomato": ("quả cà chua", [
        ("He grows tomatoes on the balcony.",
         "Anh ấy trồng cà chua ngoài ban công."),
        ("Slice the tomato thinly for the salad.",
         "Thái cà chua thật mỏng để làm rau trộn."),
    ]),
    "pizza": ("bánh pizza", [
        ("We ordered two pizzas for the team meeting.",
         "Chúng tôi đặt hai chiếc pizza cho buổi họp nhóm."),
        ("The pizza arrived cold.",
         "Chiếc pizza giao đến thì đã nguội."),
    ]),
    "hamburger": ("bánh mì kẹp thịt bò", [
        ("He ordered a hamburger without onions.",
         "Anh ấy gọi một chiếc hamburger không hành."),
        ("The stall sells hamburgers near the station.",
         "Quầy hàng gần nhà ga bán hamburger."),
    ]),
    "candy": ("kẹo", [
        ("The children shared a bag of candy.",
         "Bọn trẻ chia nhau một túi kẹo."),
        ("She keeps candy in her coat pocket.",
         "Bà ấy để kẹo trong túi áo khoác."),
    ]),
    "cookie": ("bánh quy", [
        ("He baked cookies for the office party.",
         "Anh ấy nướng bánh quy cho buổi tiệc ở văn phòng."),
        ("There is one cookie left in the tin.",
         "Trong hộp còn đúng một chiếc bánh quy."),
    ]),
    "bean": ("hạt đậu", [
        ("Soak the beans overnight before cooking.",
         "Ngâm đậu qua đêm trước khi nấu."),
        ("Coffee beans are roasted before grinding.",
         "Hạt cà phê được rang trước khi xay."),
    ]),
    "ice cream": ("kem, món tráng miệng lạnh", [
        ("The ice cream melted before we got home.",
         "Kem chảy hết trước khi chúng tôi về đến nhà."),
        ("They sell ice cream at the corner shop.",
         "Cửa hàng ở góc phố có bán kem."),
    ]),
    "delicious": ("ngon, có vị rất hấp dẫn", [
        ("The soup she cooked was absolutely delicious.",
         "Món canh cô ấy nấu ngon tuyệt."),
        ("Everything on the menu looked delicious.",
         "Mọi món trong thực đơn trông đều rất ngon."),
    ]),
    "hungry": ("đói, muốn ăn", [
        ("The children were hungry after the long walk.",
         "Bọn trẻ đói bụng sau quãng đường đi bộ dài."),
        ("I get hungry if I skip breakfast.",
         "Tôi bị đói nếu bỏ bữa sáng."),
    ]),

    # ---- Giao thông, du lịch ----
    "airport": ("sân bay", [
        ("Heavy fog closed the airport for six hours.",
         "Sương mù dày khiến sân bay đóng cửa sáu tiếng."),
        ("A shuttle bus runs from the hotel to the airport.",
         "Có xe buýt đưa đón chạy từ khách sạn ra sân bay."),
    ]),
    "airplane": ("máy bay", [
        ("The airplane landed twenty minutes early.",
         "Máy bay hạ cánh sớm hai mươi phút."),
        ("He watched the airplane rise above the clouds.",
         "Cậu bé nhìn chiếc máy bay bay lên trên tầng mây."),
    ]),
    "subway": ("tàu điện ngầm", [
        ("The subway is faster than the bus at this hour.",
         "Giờ này đi tàu điện ngầm nhanh hơn xe buýt."),
        ("She takes the subway to work every day.",
         "Cô ấy đi tàu điện ngầm đến chỗ làm mỗi ngày."),
    ]),
    "bicycle": ("xe đạp", [
        ("He repaired the bicycle himself.",
         "Anh ấy tự sửa chiếc xe đạp."),
        ("Bicycles must be parked behind the building.",
         "Xe đạp phải để ở phía sau toà nhà."),
    ]),
    "jet": ("máy bay phản lực", [
        ("A private jet was waiting on the runway.",
         "Một chiếc phản lực tư nhân đang chờ trên đường băng."),
        ("The jet flew low over the harbour.",
         "Chiếc phản lực bay thấp qua bến cảng."),
    ]),

    # ---- Mua sắm, dịch vụ ----
    "supermarket": ("siêu thị", [
        ("The supermarket opens at seven on weekdays.",
         "Siêu thị mở cửa lúc bảy giờ vào các ngày trong tuần."),
        ("She bought fruit and bread at the supermarket.",
         "Cô ấy mua trái cây và bánh mì ở siêu thị."),
    ]),
    "bookstore": ("hiệu sách", [
        ("The bookstore keeps a small reading corner.",
         "Hiệu sách có một góc đọc nhỏ."),
        ("He works part-time at a bookstore.",
         "Cậu ấy làm bán thời gian ở một hiệu sách."),
    ]),
    "waiter": ("người phục vụ bàn (nam)", [
        ("The waiter brought the bill without being asked.",
         "Người phục vụ mang hóa đơn ra mà không cần gọi."),
        ("A waiter showed us to a table by the window.",
         "Một người phục vụ dẫn chúng tôi tới bàn cạnh cửa sổ."),
    ]),
    "waitress": ("người phục vụ bàn (nữ)", [
        ("The waitress remembered our order from last week.",
         "Cô phục vụ vẫn nhớ món chúng tôi gọi tuần trước."),
        ("She worked as a waitress while studying.",
         "Cô ấy làm phục vụ bàn trong lúc đi học."),
    ]),
    "jewelry": ("đồ trang sức", [
        ("She keeps her jewelry in a locked drawer.",
         "Bà ấy cất đồ trang sức trong ngăn kéo có khoá."),
        ("The shop repairs jewelry as well as watches.",
         "Cửa hàng vừa sửa trang sức vừa sửa đồng hồ."),
    ]),
    "cloth": ("vải, mảnh vải", [
        ("Wipe the table with a damp cloth.",
         "Lau bàn bằng một mảnh vải ẩm."),
        ("The chairs are covered in dark green cloth.",
         "Những chiếc ghế được bọc vải màu xanh lá đậm."),
    ]),
    "jeans": ("quần bò, quần jean", [
        ("He wears jeans to the office on Fridays.",
         "Anh ấy mặc quần jean đến văn phòng vào thứ Sáu."),
        ("These jeans are too tight around the waist.",
         "Chiếc quần jean này chật quá ở phần eo."),
    ]),

    # ---- Thể thao, giải trí ----
    "soccer": ("môn bóng đá", [
        ("They play soccer in the park on Saturdays.",
         "Họ chơi bóng đá ở công viên vào thứ Bảy."),
        ("Her son joined the school soccer team.",
         "Con trai cô ấy vào đội bóng đá của trường."),
    ]),
    "baseball": ("môn bóng chày", [
        ("The baseball game was postponed because of rain.",
         "Trận bóng chày bị hoãn vì trời mưa."),
        ("He collects old baseball cards.",
         "Ông ấy sưu tầm thẻ bóng chày cũ."),
    ]),
    "basketball": ("môn bóng rổ", [
        ("The gym is used for basketball on Wednesday evenings.",
         "Nhà thi đấu dùng cho bóng rổ vào tối thứ Tư."),
        ("She has played basketball since primary school.",
         "Cô ấy chơi bóng rổ từ hồi tiểu học."),
    ]),
    "volleyball": ("môn bóng chuyền", [
        ("They set up a volleyball net on the beach.",
         "Họ căng lưới bóng chuyền trên bãi biển."),
        ("The volleyball match lasted two hours.",
         "Trận bóng chuyền kéo dài hai tiếng."),
    ]),
    "picnic": ("buổi dã ngoại, ăn ngoài trời", [
        ("We had a picnic beside the river.",
         "Chúng tôi đi dã ngoại bên bờ sông."),
        ("The picnic was cancelled because of the wind.",
         "Buổi dã ngoại bị hủy vì gió lớn."),
    ]),
    "zoo": ("vườn thú", [
        ("The zoo opened a new bird enclosure.",
         "Vườn thú mở khu nuôi chim mới."),
        ("Children under six enter the zoo free.",
         "Trẻ dưới sáu tuổi vào vườn thú miễn phí."),
    ]),
    "cinema": ("rạp chiếu phim", [
        ("The cinema shows old films on Sunday mornings.",
         "Rạp chiếu phim chiếu phim cũ vào sáng Chủ nhật."),
        ("We met outside the cinema at seven.",
         "Chúng tôi hẹn nhau trước rạp lúc bảy giờ."),
    ]),
    "drum": ("cái trống", [
        ("He plays the drum in a small band.",
         "Anh ấy chơi trống trong một ban nhạc nhỏ."),
        ("The drum was too loud for the small room.",
         "Tiếng trống quá to so với căn phòng nhỏ."),
    ]),
    "surf": ("lướt sóng", [
        ("They surf every morning before work.",
         "Họ đi lướt sóng mỗi sáng trước giờ làm."),
        ("It is dangerous to surf here after a storm.",
         "Lướt sóng ở đây sau bão thì rất nguy hiểm."),
    ]),
    "internet": ("mạng internet", [
        ("The internet was down for most of the afternoon.",
         "Mạng internet mất gần suốt buổi chiều."),
        ("She found the recipe on the internet.",
         "Cô ấy tìm thấy công thức trên internet."),
    ]),

    # ---- Thời tiết, sức khoẻ ----
    "sunny": ("có nắng, trời nắng", [
        ("It stayed sunny all weekend.",
         "Trời nắng suốt cả cuối tuần."),
        ("We chose a sunny table on the terrace.",
         "Chúng tôi chọn một bàn có nắng ngoài sân thượng."),
    ]),
    "cloudy": ("nhiều mây, trời âm u", [
        ("The morning was cloudy but dry.",
         "Buổi sáng trời nhiều mây nhưng không mưa."),
        ("It is too cloudy to see the mountains today.",
         "Hôm nay mây dày quá, không nhìn thấy núi."),
    ]),
    "rainy": ("có mưa, trời mưa", [
        ("The rainy season lasts from May to September.",
         "Mùa mưa kéo dài từ tháng Năm đến tháng Chín."),
        ("On rainy days the café is always full.",
         "Những ngày mưa quán cà phê lúc nào cũng đông."),
    ]),
    "snowy": ("có tuyết", [
        ("The road was snowy and hard to drive on.",
         "Đường phủ tuyết, lái xe rất khó."),
        ("They spent a snowy week in the mountains.",
         "Họ ở trên núi một tuần đầy tuyết."),
    ]),
    "sunshine": ("ánh nắng mặt trời", [
        ("The kitchen gets plenty of sunshine in the morning.",
         "Buổi sáng gian bếp có rất nhiều nắng."),
        ("After days of rain, the sunshine felt warm.",
         "Sau mấy ngày mưa, nắng lên thấy ấm hẳn."),
    ]),
    "headache": ("cơn đau đầu", [
        ("She went home early with a headache.",
         "Cô ấy về sớm vì đau đầu."),
        ("Loud noise gives him a headache.",
         "Tiếng ồn lớn làm anh ấy đau đầu."),
    ]),

    # ---- Từ chức năng, thời gian ----
    "o'clock": ("dùng sau số để nói giờ chẵn", [
        ("The meeting starts at nine o'clock.",
         "Cuộc họp bắt đầu lúc chín giờ."),
        ("She left the office at six o'clock.",
         "Cô ấy rời văn phòng lúc sáu giờ."),
    ]),
    "underline": ("gạch chân dưới chữ; nhấn mạnh", [
        ("Please underline the words you do not know.",
         "Hãy gạch chân những từ bạn chưa biết."),
        ("The report underlines the need for better training.",
         "Bản báo cáo nhấn mạnh sự cần thiết phải đào tạo tốt hơn."),
    ]),
    "bye": ("lời chào tạm biệt thân mật", [
        ("She waved and said bye from the doorway.",
         "Cô ấy vẫy tay và chào tạm biệt từ ngoài cửa."),
        ("He said bye and hung up the phone.",
         "Anh ấy chào tạm biệt rồi cúp máy."),
    ]),

    # ---- Đời sống, nhà ở — A2 ----
    "lamp": ("đèn bàn, đèn để chiếu sáng một khu vực nhỏ", [
        ("The lamp on her desk stays on until midnight.",
         "Chiếc đèn trên bàn cô ấy sáng tới tận nửa đêm."),
        ("He knocked the lamp over while reaching for the phone.",
         "Anh ấy làm đổ đèn khi với tay lấy điện thoại."),
    ]),
    "drawer": ("ngăn kéo, hộc kéo ra kéo vào trong bàn hoặc tủ", [
        ("The spare keys are in the top drawer.",
         "Chìa khoá dự phòng nằm trong ngăn kéo trên cùng."),
        ("She emptied every drawer before the move.",
         "Cô ấy dọn sạch từng ngăn kéo trước khi chuyển nhà."),
    ]),
    "oven": ("lò nướng dùng để nướng hoặc quay thức ăn", [
        ("Leave the bread in the oven for another ten minutes.",
         "Để bánh mì trong lò thêm mười phút nữa."),
        ("The oven takes a while to reach the right temperature.",
         "Lò phải mất một lúc mới đạt đúng nhiệt độ."),
    ]),
    "refrigerator": ("tủ lạnh dùng để giữ thức ăn khỏi hỏng", [
        ("There is nothing in the refrigerator but milk.",
         "Trong tủ lạnh chẳng còn gì ngoài sữa."),
        ("Put the leftovers in the refrigerator before you go out.",
         "Cất đồ ăn thừa vào tủ lạnh trước khi đi nhé."),
    ]),
    "balcony": ("ban công, phần sàn nhô ra ngoài tường có lan can", [
        ("They dry the washing on the balcony.",
         "Họ phơi quần áo ngoài ban công."),
        ("From the balcony you can see the whole market.",
         "Từ ban công có thể nhìn thấy cả khu chợ."),
    ]),
    "soap": ("xà phòng dùng để rửa tay hoặc giặt giũ", [
        ("The soap in the staff washroom has run out.",
         "Xà phòng trong nhà vệ sinh của nhân viên hết rồi."),
        ("She buys soap that has no perfume in it.",
         "Cô ấy mua loại xà phòng không có mùi thơm."),
    ]),

    # ---- Đời sống, nhà ở — B1 ----
    "trash": ("rác thải bỏ đi; ở Anh thường gọi là rubbish", [
        ("Take the trash out before the collection at seven.",
         "Đem rác ra trước giờ thu gom lúc bảy giờ."),
        ("Someone has left trash in the stairwell again.",
         "Lại có người bỏ rác ở cầu thang bộ."),
    ]),
    "furnish": ("trang bị đồ đạc cho một căn phòng hoặc căn nhà", [
        ("They furnished the flat with second-hand pieces.",
         "Họ trang bị cho căn hộ bằng đồ cũ."),
        ("The office is furnished but the wiring is not finished.",
         "Văn phòng đã có đồ nhưng hệ thống điện chưa xong."),
    ]),
    "residential": ("thuộc khu dân cư, dành để ở chứ không để kinh doanh", [
        ("Lorries are not allowed on this residential street.",
         "Xe tải không được đi vào con phố dân cư này."),
        ("The old factory is now a residential development.",
         "Nhà máy cũ giờ đã thành một khu nhà ở."),
    ]),
    "outdoor": ("ngoài trời, diễn ra hoặc dùng ở bên ngoài nhà", [
        ("The outdoor seating is closed during the winter.",
         "Khu chỗ ngồi ngoài trời đóng cửa suốt mùa đông."),
        ("She keeps her outdoor shoes by the back door.",
         "Cô ấy để giày đi ngoài trời cạnh cửa sau."),
    ]),
    "sidewalk": ("vỉa hè dành cho người đi bộ; ở Anh gọi là pavement", [
        ("The sidewalk was icy early this morning.",
         "Vỉa hè đóng băng vào sáng sớm nay."),
        ("Cyclists should not ride on the sidewalk.",
         "Người đi xe đạp không nên đi trên vỉa hè."),
    ]),
    "overnight": ("qua đêm, kéo dài từ tối hôm trước sang sáng hôm sau", [
        ("The parcel was left overnight at the depot.",
         "Kiện hàng nằm lại kho qua đêm."),
        ("She stayed overnight rather than driving home late.",
         "Cô ấy ở lại qua đêm thay vì lái xe về muộn."),
    ]),

    # ---- Mua sắm, tiền bạc — A1 ----
    "money": ("tiền, thứ dùng để mua bán và trả công", [
        ("He saves money by taking sandwiches to work.",
         "Anh ấy tiết kiệm tiền bằng cách mang bánh mì đi làm."),
        ("The money for the trip is due on Friday.",
         "Tiền cho chuyến đi phải nộp vào thứ Sáu."),
    ]),
    "price": ("giá, số tiền phải trả cho một món hàng", [
        ("The price on the label is wrong.",
         "Giá ghi trên nhãn bị sai."),
        ("Prices went up again at the start of the month.",
         "Giá lại tăng vào đầu tháng."),
    ]),
    "buy": ("mua, trả tiền để lấy một thứ gì đó", [
        ("She wants to buy a bicycle before the summer.",
         "Cô ấy muốn mua một chiếc xe đạp trước mùa hè."),
        ("We bought the tickets online last night.",
         "Tối qua chúng tôi đã mua vé trên mạng."),
    ]),
    "pay": ("trả tiền cho hàng hoá, dịch vụ hoặc công việc", [
        ("You can pay at the desk by the entrance.",
         "Bạn có thể trả tiền tại quầy cạnh lối vào."),
        ("They paid for the meal separately.",
         "Họ trả tiền bữa ăn riêng từng người."),
    ]),
    "order": ("đơn đặt hàng; cũng là hành động đặt mua", [
        ("Your order will arrive on Tuesday.",
         "Đơn hàng của bạn sẽ tới vào thứ Ba."),
        ("He ordered two coffees and a sandwich.",
         "Anh ấy gọi hai cà phê và một chiếc bánh mì."),
    ]),
    "spend": ("tiêu tiền; cũng dùng cho việc dành thời gian", [
        ("They spend too much on takeaway food.",
         "Họ tiêu quá nhiều tiền vào đồ ăn mang về."),
        ("I spent an hour looking for the receipt.",
         "Tôi mất một tiếng đồng hồ đi tìm hoá đơn."),
    ]),

    # ---- Mua sắm, tiền bạc — A2 ----
    "receipt": ("hoá đơn, giấy chứng nhận đã thanh toán", [
        ("Keep the receipt in case you need to return it.",
         "Giữ hoá đơn lại phòng khi bạn cần trả hàng."),
        ("She could not claim the expense without a receipt.",
         "Cô ấy không thể xin hoàn tiền vì không có hoá đơn."),
    ]),
    "bargain": ("món hời, thứ mua được với giá rẻ hơn bình thường", [
        ("The coat was a real bargain in the winter sale.",
         "Chiếc áo khoác đúng là món hời trong đợt giảm giá mùa đông."),
        ("He never buys anything unless it is a bargain.",
         "Anh ấy không mua gì trừ khi đó là món hời."),
    ]),
    "wallet": ("ví đựng tiền và thẻ, thường bỏ túi", [
        ("He left his wallet on the counter.",
         "Anh ấy để quên ví trên quầy."),
        ("There was nothing in the wallet but a bus pass.",
         "Trong ví chẳng có gì ngoài chiếc vé xe buýt."),
    ]),
    "mall": ("trung tâm thương mại có nhiều cửa hàng dưới một mái", [
        ("The mall stays open until nine at weekends.",
         "Trung tâm thương mại mở tới chín giờ vào cuối tuần."),
        ("Parking at the mall is free for two hours.",
         "Đỗ xe ở trung tâm thương mại miễn phí hai tiếng."),
    ]),
    "convenience": ("sự tiện lợi; cũng chỉ cửa hàng tiện lợi", [
        ("She chose the flat for its convenience, not its size.",
         "Cô ấy chọn căn hộ vì tiện lợi, không phải vì rộng."),
        ("There is a convenience store on the corner.",
         "Có một cửa hàng tiện lợi ở góc phố."),
    ]),
    "inexpensive": ("không đắt, có giá phải chăng", [
        ("They found an inexpensive hotel near the station.",
         "Họ tìm được một khách sạn không đắt gần ga."),
        ("The material is strong and surprisingly inexpensive.",
         "Vật liệu này chắc chắn mà giá lại rẻ đến bất ngờ."),
    ]),

    # ---- Tài chính, kế toán — B2 ----
    "audit": ("cuộc kiểm toán, việc rà soát chính thức sổ sách hoặc quy trình", [
        ("The annual audit begins next Monday.",
         "Cuộc kiểm toán thường niên bắt đầu vào thứ Hai tới."),
        ("Two errors were found during the internal audit.",
         "Hai sai sót đã được phát hiện trong đợt kiểm toán nội bộ."),
    ]),
    "deduction": ("khoản khấu trừ khỏi số tiền phải trả hoặc được nhận", [
        ("The deduction for the pension appears on every payslip.",
         "Khoản khấu trừ tiền hưu xuất hiện trên mọi phiếu lương."),
        ("After deductions, the figure is considerably lower.",
         "Sau các khoản khấu trừ, con số thấp hơn đáng kể."),
    ]),
    "profitable": ("có lãi, mang lại lợi nhuận", [
        ("The branch became profitable in its second year.",
         "Chi nhánh bắt đầu có lãi từ năm thứ hai."),
        ("Not every profitable contract is worth renewing.",
         "Không phải hợp đồng có lãi nào cũng đáng gia hạn."),
    ]),
    "economical": ("tiết kiệm, dùng ít tiền hoặc ít nguyên liệu", [
        ("The smaller engine is more economical over long distances.",
         "Động cơ nhỏ hơn tiết kiệm hơn khi đi đường dài."),
        ("Buying in bulk is economical only if the stock is used.",
         "Mua sỉ chỉ tiết kiệm nếu hàng được dùng hết."),
    ]),
    "withdrawal": ("việc rút tiền khỏi tài khoản; cũng là việc rút lui", [
        ("A withdrawal of that size needs two signatures.",
         "Một khoản rút tiền cỡ đó cần hai chữ ký."),
        ("The withdrawal appeared on the statement the next day.",
         "Khoản rút tiền xuất hiện trên sao kê vào ngày hôm sau."),
    ]),
    "retailer": ("nhà bán lẻ, đơn vị bán hàng trực tiếp cho người tiêu dùng", [
        ("The retailer refused to refund without proof of purchase.",
         "Nhà bán lẻ từ chối hoàn tiền nếu không có bằng chứng mua hàng."),
        ("Several retailers have closed their high-street branches.",
         "Nhiều nhà bán lẻ đã đóng cửa các chi nhánh trên phố chính."),
    ]),

    # ---- Du lịch, giao thông — A1 ----
    "car": ("xe ô tô con", [
        ("Their car is parked behind the building.",
         "Xe của họ đỗ phía sau toà nhà."),
        ("She goes to work by car twice a week.",
         "Cô ấy đi làm bằng ô tô hai lần một tuần."),
    ]),
    "train": ("tàu hoả chạy trên đường ray", [
        ("The train to Ashcombe leaves from platform two.",
         "Tàu đi Ashcombe khởi hành từ sân ga số hai."),
        ("We missed the last train and had to take a taxi.",
         "Chúng tôi lỡ chuyến tàu cuối và phải bắt taxi."),
    ]),
    "drive": ("lái xe; cũng chỉ chuyến đi bằng ô tô", [
        ("He drives to the depot every morning.",
         "Sáng nào anh ấy cũng lái xe tới kho."),
        ("It is a two-hour drive from here.",
         "Từ đây lái xe mất hai tiếng."),
    ]),
    "visit": ("thăm, đến một nơi hoặc gặp một người trong thời gian ngắn", [
        ("They visit her parents every other weekend.",
         "Cứ cách một tuần họ lại về thăm bố mẹ cô ấy."),
        ("The visit lasted less than an hour.",
         "Chuyến thăm kéo dài chưa tới một tiếng."),
    ]),
    "city": ("thành phố, khu dân cư lớn", [
        ("She moved to the city for work last year.",
         "Năm ngoái cô ấy chuyển lên thành phố để đi làm."),
        ("The city centre is closed to traffic on Sundays.",
         "Trung tâm thành phố cấm xe vào Chủ nhật."),
    ]),
    "town": ("thị trấn, khu dân cư nhỏ hơn thành phố", [
        ("The town has one bank and two schools.",
         "Thị trấn có một ngân hàng và hai trường học."),
        ("We drove through several small towns on the way.",
         "Trên đường chúng tôi đi qua vài thị trấn nhỏ."),
    ]),

    # ---- Công tác, đi lại — B1 ----
    "luggage": ("hành lý mang theo khi đi lại", [
        ("Leave your luggage at reception until the room is ready.",
         "Cứ để hành lý ở lễ tân cho tới khi phòng sẵn sàng."),
        ("His luggage went to the wrong airport.",
         "Hành lý của anh ấy bị chuyển nhầm sân bay."),
    ]),
    "passport": ("hộ chiếu, giấy tờ tuỳ thân dùng khi xuất nhập cảnh", [
        ("Check that your passport has six months left on it.",
         "Kiểm tra xem hộ chiếu còn hạn ít nhất sáu tháng không."),
        ("She renewed her passport before booking anything.",
         "Cô ấy gia hạn hộ chiếu trước khi đặt bất cứ thứ gì."),
    ]),
    "destination": ("điểm đến, nơi một chuyến đi kết thúc", [
        ("The final destination is printed on your ticket.",
         "Điểm đến cuối cùng được in trên vé của bạn."),
        ("Bad weather closed the airport at their destination.",
         "Thời tiết xấu khiến sân bay ở điểm đến phải đóng cửa."),
    ]),
    "depart": ("khởi hành, rời đi để bắt đầu một chuyến đi", [
        ("The coach departs at a quarter to six.",
         "Xe khách khởi hành lúc năm giờ bốn mươi lăm."),
        ("Flights departing after nine are unaffected.",
         "Các chuyến bay khởi hành sau chín giờ không bị ảnh hưởng."),
    ]),
    "departure": ("sự khởi hành; cũng chỉ chuyến đi trên bảng thông báo", [
        ("Check the departure board before you go through.",
         "Xem bảng khởi hành trước khi đi qua cửa kiểm soát."),
        ("The departure was delayed by nearly two hours.",
         "Giờ khởi hành bị hoãn gần hai tiếng."),
    ]),
    "ferry": ("phà chở người và xe qua sông hoặc qua biển", [
        ("The ferry runs every forty minutes in summer.",
         "Mùa hè phà chạy bốn mươi phút một chuyến."),
        ("They took the overnight ferry to save a night's hotel.",
         "Họ đi phà đêm để tiết kiệm một đêm khách sạn."),
    ]),

    # ---- Ăn uống, giải trí — A2 ----
    "chef": ("đầu bếp, người nấu ăn chuyên nghiệp", [
        ("The chef changes the menu every season.",
         "Đầu bếp đổi thực đơn theo từng mùa."),
        ("She trained as a chef before opening the café.",
         "Cô ấy học nghề đầu bếp trước khi mở quán cà phê."),
    ]),
    "dessert": ("món tráng miệng ăn sau bữa chính", [
        ("We were too full for dessert.",
         "Chúng tôi no quá không ăn nổi tráng miệng."),
        ("The dessert menu is on the back of the card.",
         "Thực đơn tráng miệng nằm ở mặt sau tấm thực đơn."),
    ]),
    "snack": ("đồ ăn nhẹ giữa các bữa chính", [
        ("There are snacks in the meeting room.",
         "Có đồ ăn nhẹ trong phòng họp."),
        ("He had a snack on the train instead of lunch.",
         "Anh ấy ăn nhẹ trên tàu thay cho bữa trưa."),
    ]),
    "cafeteria": ("nhà ăn tự phục vụ trong trường học hoặc công ty", [
        ("The cafeteria stops serving hot food at two.",
         "Nhà ăn ngừng phục vụ đồ nóng lúc hai giờ."),
        ("Most staff eat in the cafeteria on the ground floor.",
         "Phần lớn nhân viên ăn ở nhà ăn tầng trệt."),
    ]),
    "bake": ("nướng bánh hoặc nướng thức ăn trong lò", [
        ("She bakes bread every Sunday morning.",
         "Sáng Chủ nhật nào cô ấy cũng nướng bánh mì."),
        ("Bake the dish for forty minutes without the lid.",
         "Nướng món này bốn mươi phút, không đậy nắp."),
    ]),
    "pasta": ("mì Ý, món ăn làm từ bột mì tạo hình", [
        ("The pasta here is made on the premises.",
         "Mì Ý ở đây được làm ngay tại quán."),
        ("He cooked pasta because it was quick.",
         "Anh ấy nấu mì Ý vì nhanh."),
    ]),

    # ---- Sự kiện, dịch vụ tiếp đón — B1 ----
    "ingredient": ("nguyên liệu, thành phần dùng để nấu một món ăn", [
        ("The recipe needs only five ingredients.",
         "Công thức này chỉ cần năm nguyên liệu."),
        ("Please tell the kitchen about any ingredient you cannot eat.",
         "Xin báo nhà bếp nếu có nguyên liệu nào bạn không ăn được."),
    ]),
    "flavor": ("hương vị của món ăn hoặc đồ uống", [
        ("The soup has a stronger flavor than I expected.",
         "Món súp có vị đậm hơn tôi tưởng."),
        ("They offer six flavors of ice cream.",
         "Họ có sáu vị kem."),
    ]),
    "vegetarian": ("người ăn chay; cũng là món không có thịt", [
        ("Two of the guests are vegetarian.",
         "Hai vị khách ăn chay."),
        ("There is one vegetarian option on the set menu.",
         "Thực đơn cố định có một lựa chọn chay."),
    ]),
    "reservation": ("việc đặt chỗ trước ở nhà hàng, khách sạn hoặc chuyến đi", [
        ("The reservation is under the name Okonkwo.",
         "Chỗ đặt dưới tên Okonkwo."),
        ("They lost our reservation and gave the table away.",
         "Họ làm mất chỗ đặt của chúng tôi và cho người khác ngồi."),
    ]),
    "fountain": ("đài phun nước; cũng chỉ vòi nước uống công cộng", [
        ("We agreed to meet by the fountain in the square.",
         "Chúng tôi hẹn gặp nhau ở đài phun nước giữa quảng trường."),
        ("The fountain is switched off during the repairs.",
         "Đài phun nước tắt trong thời gian sửa chữa."),
    ]),
    "admission": ("vé vào cửa, quyền được vào một nơi", [
        ("Admission is free for children under twelve.",
         "Trẻ dưới mười hai tuổi được vào cửa miễn phí."),
        ("Admission closes half an hour before the museum does.",
         "Cửa bán vé đóng trước bảo tàng nửa tiếng."),
    ]),

    # ---- Sức khoẻ — A2 ----
    "pill": ("viên thuốc uống", [
        ("Take one pill in the morning with food.",
         "Uống một viên vào buổi sáng cùng thức ăn."),
        ("She forgot her pills at home.",
         "Cô ấy để quên thuốc ở nhà."),
    ]),
    "dentist": ("nha sĩ, bác sĩ chữa răng", [
        ("He has not been to the dentist for three years.",
         "Ba năm rồi anh ấy chưa đi nha sĩ."),
        ("The dentist is on the first floor, above the pharmacy.",
         "Phòng nha ở tầng một, phía trên hiệu thuốc."),
    ]),
    "harmful": ("có hại, gây tổn hại cho sức khoẻ hoặc cho vật gì đó", [
        ("The fumes are harmful in an enclosed space.",
         "Khói này có hại trong không gian kín."),
        ("Too much sitting is harmful over time.",
         "Ngồi quá nhiều về lâu dài là có hại."),
    ]),
    "asleep": ("đang ngủ", [
        ("The baby was asleep before the car left the street.",
         "Em bé đã ngủ trước khi xe ra khỏi phố."),
        ("He fell asleep during the second half of the film.",
         "Anh ấy ngủ gật trong nửa sau bộ phim."),
    ]),
    "energetic": ("tràn đầy năng lượng, hoạt bát", [
        ("She is remarkably energetic for someone who works nights.",
         "Cô ấy hoạt bát đến lạ so với một người làm ca đêm."),
        ("The class is energetic and quite noisy.",
         "Lớp học sôi nổi và khá ồn."),
    ]),
    "unhappy": ("không vui, buồn bã hoặc không hài lòng", [
        ("Several parents were unhappy about the change.",
         "Nhiều phụ huynh không hài lòng về thay đổi này."),
        ("He looked unhappy but said nothing.",
         "Anh ấy trông buồn nhưng không nói gì."),
    ]),

    # ---- Sức khoẻ, an toàn — B1 ----
    "flu": ("bệnh cúm", [
        ("Half the department has had flu this month.",
         "Tháng này một nửa phòng bị cúm."),
        ("She was off with flu for a week.",
         "Cô ấy nghỉ ốm vì cúm một tuần."),
    ]),
    "prescription": ("đơn thuốc do bác sĩ kê", [
        ("The prescription can be collected from any pharmacy.",
         "Đơn thuốc có thể lấy ở bất kỳ hiệu thuốc nào."),
        ("This medicine is only available on prescription.",
         "Thuốc này chỉ bán khi có đơn."),
    ]),
    "clinic": ("phòng khám, nơi khám và điều trị ngoại trú", [
        ("The clinic opens at eight but the queue starts earlier.",
         "Phòng khám mở lúc tám giờ nhưng người ta xếp hàng sớm hơn."),
        ("She works two days a week at a clinic in Ardleigh.",
         "Cô ấy làm hai ngày một tuần ở một phòng khám tại Ardleigh."),
    ]),
    "pharmacy": ("hiệu thuốc, nơi bán và pha chế thuốc", [
        ("The pharmacy next to the station stays open late.",
         "Hiệu thuốc cạnh nhà ga mở cửa tới khuya."),
        ("Ask at the pharmacy whether the two can be taken together.",
         "Hỏi hiệu thuốc xem hai loại này có uống cùng nhau được không."),
    ]),
    "jog": ("chạy bộ chậm để rèn luyện sức khoẻ", [
        ("He jogs along the canal before work.",
         "Anh ấy chạy bộ dọc con kênh trước giờ làm."),
        ("A short jog is enough to warm up.",
         "Chạy bộ một quãng ngắn là đủ để khởi động."),
    ]),
    "nap": ("giấc ngủ ngắn, thường vào ban ngày", [
        ("A twenty-minute nap leaves her sharper than coffee does.",
         "Ngủ hai mươi phút giúp cô ấy tỉnh táo hơn cà phê."),
        ("He took a nap on the coach and missed the view.",
         "Anh ấy ngủ một giấc trên xe khách và bỏ lỡ cảnh đẹp."),
    ]),
}


# Gán chủ đề bằng tay cho những từ được chọn có chủ đích. Suy chủ đề từ lexname
# của WordNet chỉ đúng khoảng 45% (đã đo) vì WordNet gộp toàn bộ tính từ vào
# adj.all, nên với các từ dưới đây thì gán tay là cách duy nhất chính xác.
TOPIC_OVERRIDE: dict[str, str] = {
    # vocab_daily_life_a2
    "lamp": "vocab_daily_life_a2",
    "drawer": "vocab_daily_life_a2",
    "oven": "vocab_daily_life_a2",
    "refrigerator": "vocab_daily_life_a2",
    "balcony": "vocab_daily_life_a2",
    "soap": "vocab_daily_life_a2",
    # vocab_daily_life_b1
    "trash": "vocab_daily_life_b1",
    "furnish": "vocab_daily_life_b1",
    "residential": "vocab_daily_life_b1",
    "outdoor": "vocab_daily_life_b1",
    "sidewalk": "vocab_daily_life_b1",
    "overnight": "vocab_daily_life_b1",
    # vocab_shopping_finance_a1
    "money": "vocab_shopping_finance_a1",
    "price": "vocab_shopping_finance_a1",
    "buy": "vocab_shopping_finance_a1",
    "pay": "vocab_shopping_finance_a1",
    "order": "vocab_shopping_finance_a1",
    "spend": "vocab_shopping_finance_a1",
    # vocab_shopping_finance_a2
    "receipt": "vocab_shopping_finance_a2",
    "bargain": "vocab_shopping_finance_a2",
    "wallet": "vocab_shopping_finance_a2",
    "mall": "vocab_shopping_finance_a2",
    "convenience": "vocab_shopping_finance_a2",
    "inexpensive": "vocab_shopping_finance_a2",
    # vocab_shopping_finance_b2
    "audit": "vocab_shopping_finance_b2",
    "deduction": "vocab_shopping_finance_b2",
    "profitable": "vocab_shopping_finance_b2",
    "economical": "vocab_shopping_finance_b2",
    "withdrawal": "vocab_shopping_finance_b2",
    "retailer": "vocab_shopping_finance_b2",
    # vocab_travel_transport_a1
    "car": "vocab_travel_transport_a1",
    "train": "vocab_travel_transport_a1",
    "drive": "vocab_travel_transport_a1",
    "visit": "vocab_travel_transport_a1",
    "city": "vocab_travel_transport_a1",
    "town": "vocab_travel_transport_a1",
    # vocab_travel_transport_b1
    "luggage": "vocab_travel_transport_b1",
    "passport": "vocab_travel_transport_b1",
    "destination": "vocab_travel_transport_b1",
    "depart": "vocab_travel_transport_b1",
    "departure": "vocab_travel_transport_b1",
    "ferry": "vocab_travel_transport_b1",
    # vocab_dining_entertainment_a2
    "chef": "vocab_dining_entertainment_a2",
    "dessert": "vocab_dining_entertainment_a2",
    "snack": "vocab_dining_entertainment_a2",
    "cafeteria": "vocab_dining_entertainment_a2",
    "bake": "vocab_dining_entertainment_a2",
    "pasta": "vocab_dining_entertainment_a2",
    # vocab_dining_entertainment_b1
    "ingredient": "vocab_dining_entertainment_b1",
    "flavor": "vocab_dining_entertainment_b1",
    "vegetarian": "vocab_dining_entertainment_b1",
    "reservation": "vocab_dining_entertainment_b1",
    "fountain": "vocab_dining_entertainment_b1",
    "admission": "vocab_dining_entertainment_b1",
    # vocab_health_wellbeing_a2
    "pill": "vocab_health_wellbeing_a2",
    "dentist": "vocab_health_wellbeing_a2",
    "harmful": "vocab_health_wellbeing_a2",
    "asleep": "vocab_health_wellbeing_a2",
    "energetic": "vocab_health_wellbeing_a2",
    "unhappy": "vocab_health_wellbeing_a2",
    # vocab_health_wellbeing_b1
    "flu": "vocab_health_wellbeing_b1",
    "prescription": "vocab_health_wellbeing_b1",
    "clinic": "vocab_health_wellbeing_b1",
    "pharmacy": "vocab_health_wellbeing_b1",
    "jog": "vocab_health_wellbeing_b1",
    "nap": "vocab_health_wellbeing_b1",
}


# Collocation viết tay cho từ B2/C1 — schema bắt buộc ≥3 cụm ở hai mức này.
# Blocker B9 là về NGUỒN 4 200 cụm (Oxford/Macmillan có bản quyền); tự viết vài
# chục cụm cho đúng những từ mình đã chọn thì không vướng gì.
#
#   lemma -> [(pattern, text, cefr)]
COLLOCATIONS: dict[str, list[tuple[str, str, str]]] = {
    "audit": [("ADJ+N", "internal audit", "B2"),
              ("V+N", "carry out an audit", "B2"),
              ("N+N", "audit trail", "C1")],
    "deduction": [("ADJ+N", "statutory deduction", "B2"),
                  ("V+N", "make a deduction", "B2"),
                  ("PREP+N", "after deductions", "B2")],
    "profitable": [("ADV+ADJ", "highly profitable", "B2"),
                   ("ADJ+N", "profitable venture", "B2"),
                   ("V+N", "become profitable", "B2")],
    "economical": [("ADV+ADJ", "more economical", "B2"),
                   ("ADJ+N", "economical option", "B2"),
                   ("ADJ+N", "economical use of space", "C1")],
    "withdrawal": [("ADJ+N", "cash withdrawal", "B2"),
                   ("V+N", "make a withdrawal", "B2"),
                   ("N+N", "withdrawal limit", "B2")],
    "retailer": [("ADJ+N", "online retailer", "B2"),
                 ("ADJ+N", "independent retailer", "B2"),
                 ("ADJ+N", "major retailer", "B2")],
}
