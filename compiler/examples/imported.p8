%zeropage full
%address 33
%option enable_floats
%option enable_floats

~ extra  {
	; this is imported

	X = 42
	return 44
}

~ extra2  {
	; this is imported

	X = 42
	return 44

label_in_extra2:
	X = 33
	return

	sub sub_in_extra2() -> () {
		return
	}
	sub another_sub_in_extra2() -> () {
		return
	}
}


~ main2  {
	; this is imported

	X = 42
	return 44
}
